package com.manavault.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** Background download of a FULL snapshot or incremental artwork delta.
 * Never blocks CameraX/OCR, never downloads Scryfall images during scanning.
 * The GitHub data workflow publishes a full snapshot and, after the first
 * version, a signed-by-checksum replacement delta. Scanning never waits.
 */
final class FullArtworkIndexUpdater {
    private static final String TAG="ManaVaultFullArtUpdate";
    private static final String BASE="https://github.com/jarredlipp/manavault/releases/download/artwork-db/";
    private static final ExecutorService POOL=Executors.newSingleThreadExecutor();
    private static volatile boolean running;
    private static volatile String detail="Full artwork database not downloaded";
    interface Callback {void onFinished(JSONObject result);}
    static String status(){return detail;}
    static void request(Context context,Callback callback){
        if(running){reply(callback,false,"Full artwork download already running");return;}
        running=true;Context app=context.getApplicationContext();
        POOL.execute(()->{
            try{download(app);reply(callback,true,detail);}
            catch(Exception e){detail="Full artwork update failed: "+e.getMessage();Log.w(TAG,detail,e);reply(callback,false,detail);}
            finally{running=false;}
        });
    }
    private static void reply(Callback callback,boolean ok,String message){
        try{callback.onFinished(new JSONObject().put("ok",ok).put("message",message));}
        catch(Exception ignored){}
    }
    private static HttpURLConnection connect(String address)throws Exception {
        // Redirects for public GitHub Release assets are expected; Java verifies
        // final bytes against SHA-256 from the published manifest before use.
        URL uri=new URL(address);
        if(!"https".equals(uri.getProtocol()))throw new SecurityException("HTTPS is required");
        HttpURLConnection conn=(HttpURLConnection)uri.openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(50000);
        conn.setRequestProperty("User-Agent","ManaVault/5.12.31 offline artwork updater");
        if(conn.getResponseCode()!=200){int status=conn.getResponseCode();conn.disconnect();throw new java.io.IOException("Artwork release HTTP "+status+" (publish artwork-db first)");}
        return conn;
    }
    private static void copyLimited(InputStream in,OutputStream out,long max,MessageDigest hash)throws Exception{
        byte[] buffer=new byte[32768];int read;long count=0;
        while((read=in.read(buffer))!=-1){count+=read;if(count>max)throw new java.io.IOException("Artwork database exceeds storage limit");out.write(buffer,0,read);if(hash!=null)hash.update(buffer,0,read);}
    }
    private static void download(Context context)throws Exception {
        detail="Checking versioned complete-artwork release…";
        HttpURLConnection metadata=connect(BASE+"manavault-artwork-manifest.json");
        byte[] manifestBytes;
        try(InputStream in=metadata.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            copyLimited(in,out,65536,null);manifestBytes=out.toByteArray();
        }finally{metadata.disconnect();}
        JSONObject meta=new JSONObject(new String(manifestBytes,java.nio.charset.StandardCharsets.UTF_8));
        if(meta.optInt("schema")!=1)throw new java.io.IOException("Unsupported offline artwork schema");
        String version=meta.optString("version","");String hash=meta.optString("sha256","");
        String file=meta.optString("file","");
        if(!version.matches("[0-9]{8}T[0-9]{6}Z")||!hash.matches("[0-9a-f]{64}")||
           !"manavault-artwork.sqlite.gz".equals(file))throw new SecurityException("Invalid artwork release manifest");
        String installedVersion=FullArtworkIndex.installedVersion(context);
        if(version.equals(installedVersion)){detail="Full artwork index already current ("+version+")";return;}
        JSONObject delta=meta.optJSONObject("delta");
        boolean incremental=delta!=null && !installedVersion.isEmpty() &&
            installedVersion.equals(delta.optString("from","")) &&
            "manavault-artwork-delta.sqlite.gz".equals(delta.optString("file","")) &&
            delta.optString("sha256","").matches("[0-9a-f]{64}");
        if(incremental){file=delta.optString("file");hash=delta.optString("sha256");}
        final boolean useDelta=incremental;
        File dir=context.getFilesDir();File zipped=new File(dir,"artwork-index-download.tmp");
        File staged=new File(dir,"artwork-index-stage.tmp");
        File installed=new File(dir,FullArtworkIndex.FILE);
        try {
            detail="Downloading compressed artwork database; scanning stays available…";
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            HttpURLConnection data=connect(BASE+file);
            try(InputStream in=data.getInputStream();OutputStream out=new FileOutputStream(zipped)){
                copyLimited(in,out,350L*1024*1024,sha);
            }finally{data.disconnect();}
            byte[] digest=sha.digest();StringBuilder actual=new StringBuilder();
            for(byte b:digest)actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!hash.equals(actual.toString()))throw new SecurityException("Artwork release checksum mismatch");
            detail="Verifying offline SQLite artwork index…";
            try(InputStream in=new GZIPInputStream(new FileInputStream(zipped));
                OutputStream out=new FileOutputStream(staged)){
                copyLimited(in,out,900L*1024*1024,null);
            }
            if(useDelta){
                detail="Applying verified incremental printing changes…";
                File deltaFile=staged;
                File assembled=new File(dir,"artwork-index-rebuilt.tmp");
                try{
                    if(!installed.isFile())throw new java.io.IOException("Base artwork index missing");
                    Files.copy(installed.toPath(),assembled.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    applyDelta(assembled,deltaFile,installedVersion,version);
                    // The replacement must be the completed copy, never the delta.
                    Files.move(assembled.toPath(),staged.toPath(),StandardCopyOption.REPLACE_EXISTING);
                }finally{assembled.delete();}
            }
            SQLiteDatabase verify=SQLiteDatabase.openDatabase(staged.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            try{
                if(!"ok".equals(verifyIntegrity(verify)))throw new java.io.IOException("Artwork SQLite integrity check failed");
                long count=android.database.DatabaseUtils.longForQuery(verify,"SELECT COUNT(*) FROM artwork",null);
                if(count!=meta.optLong("indexed_faces") || count<1)throw new java.io.IOException("Unexpected artwork row count");
                if(!version.equals(lookupVersion(verify)))throw new java.io.IOException("Artwork index version mismatch");
            }finally{verify.close();}
            // The currently installed database is never mutated. On success
            // a complete, verified replacement is swapped into app storage.
            FullArtworkIndex.close();
            Files.move(staged.toPath(),installed.toPath(),StandardCopyOption.REPLACE_EXISTING);
            detail="Complete offline artwork index ready: "+meta.optLong("indexed_faces")+" faces ("+version+")";
        }finally{zipped.delete();staged.delete();}
    }
    /** Apply a SHA-256-verified SQLite delta to a COPY of the previous index.
     * Cross-check base version, keep 8 perceptual hash bands synchronized,
     * and reject partial updates before touching the installed database.
     */
    private static void applyDelta(File target,File delta,String oldVersion,String newVersion)throws Exception{
        SQLiteDatabase destination=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,
            SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        SQLiteDatabase changes=SQLiteDatabase.openDatabase(delta.getAbsolutePath(),null,
            SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try{
            if(!"ok".equals(verifyIntegrity(changes))||!"ok".equals(verifyIntegrity(destination)))
                throw new java.io.IOException("Invalid delta or base database");
            if(!oldVersion.equals(lookupVersion(destination)))throw new java.io.IOException("Wrong delta base version");
            String from="";
            try(android.database.Cursor c=changes.rawQuery("SELECT value FROM meta WHERE key='from_version'",null)){
                if(c.moveToFirst())from=c.getString(0);
            }
            if(!from.equals(oldVersion)||!newVersion.equals(lookupVersion(changes)))
                throw new java.io.IOException("Delta version does not match manifest");
            destination.beginTransaction();
            try {
                try(android.database.Cursor c=changes.rawQuery("SELECT printingId,face FROM removed",null)){
                    while(c.moveToNext())removeRow(destination,c.getString(0),c.getInt(1));
                }
                try(android.database.Cursor c=changes.rawQuery("SELECT * FROM artwork",null)){
                    while(c.moveToNext()){
                        String id=c.getString(0);int face=c.getInt(1);
                        removeRow(destination,id,face);
                        Object[] values=new Object[16];
                        for(int j=0;j<16;j++)values[j]=(j==1)?face:
                            (j>=8 && j<=14)?c.getBlob(j):c.getString(j);
                        destination.execSQL("INSERT INTO artwork VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",values);
                        long hash=Long.parseUnsignedLong(c.getString(15),16);
                        for(int band=0;band<8;band++)
                            destination.execSQL("INSERT INTO art_band VALUES(?,?,?,?)",
                                new Object[]{band,(hash>>(band*8))&255,id,face});
                    }
                }
                destination.execSQL("INSERT OR REPLACE INTO meta VALUES(?,?)",new Object[]{"version",newVersion});
                destination.setTransactionSuccessful();
            }finally{destination.endTransaction();}
        }finally{changes.close();destination.close();}
    }
    private static void removeRow(SQLiteDatabase db,String id,int face){
        db.execSQL("DELETE FROM art_band WHERE printingId=? AND face=?",new Object[]{id,face});
        db.execSQL("DELETE FROM artwork WHERE printingId=? AND face=?",new Object[]{id,face});
    }
    private static String lookupVersion(SQLiteDatabase db){
        try(android.database.Cursor c=db.rawQuery("SELECT value FROM meta WHERE key='version'",null)){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    private static String verifyIntegrity(SQLiteDatabase db){
        try(android.database.Cursor c=db.rawQuery("PRAGMA quick_check",null)){
            return c.moveToFirst()?c.getString(0):"failed";
        }
    }
}
