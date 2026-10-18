package com.manavault.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** Immutable, indexed Scryfall catalog bundled in the APK. All visual
 * assertions are performed by the resolver, never by catalog lookup itself.
 * Older APKs / builds without the bundled catalog retain the IndexedDB path.
 */
final class NativePrintingIndex {
    private static final String TAG="ManaVaultPrintingDB";
    private static final String ASSET="printing-catalog.sqlite.gz";
    private static final String FILE="printing-catalog-5115.sqlite";
    private static volatile SQLiteDatabase database;
    private static volatile String state="Preparing offline catalog";
    private static volatile String sourceStamp="";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private NativePrintingIndex(){}

    static void start(Context context){
        IO.execute(()->install(context.getApplicationContext()));
    }
    private static void install(Context context){
        try {
            File dst=new File(context.getFilesDir(),FILE);
            if(!valid(dst)){
                File tmp=new File(context.getFilesDir(),FILE+".tmp");
                if(tmp.exists())tmp.delete();
                try(InputStream in=new GZIPInputStream(context.getAssets().open(ASSET));
                    OutputStream out=new FileOutputStream(tmp)){
                    byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                if(!valid(tmp))throw new IllegalStateException("Bundled catalog failed integrity check");
                if(dst.exists()&&!dst.delete())throw new IllegalStateException("Cannot replace old printing catalog");
                if(!tmp.renameTo(dst))throw new IllegalStateException("Cannot install native printing catalog");
            }
            SQLiteDatabase opened=SQLiteDatabase.openDatabase(dst.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            int count;
            try(Cursor c=opened.rawQuery("SELECT COUNT(*) FROM printings",null)){c.moveToFirst();count=c.getInt(0);}
            try(Cursor c=opened.rawQuery("SELECT value FROM info WHERE key='source'",null)){
                if(c.moveToFirst())sourceStamp=c.getString(0);
            }
            database=opened;
            state="Ready: "+count+" offline native printings";
            Log.i(TAG,state);
        }catch(Exception e){state="IndexedDB fallback: "+e.getMessage();Log.w(TAG,state,e);}
    }
    private static boolean valid(File path){
        if(!path.isFile()||path.length()<4096)return false;
        try(SQLiteDatabase d=SQLiteDatabase.openDatabase(path.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM printings",null)){
                return c.moveToFirst() && c.getInt(0)>=100000;
            }
        }catch(Exception e){return false;}
    }
    static String status(){return state;}
    static String version(){return sourceStamp;}
    static String lookup(String criterion,String term,int max){
        SQLiteDatabase db=database;
        JSONObject out=new JSONObject();JSONArray rows=new JSONArray();
        try{
            out.put("ready",db!=null);out.put("source","native SQLite");out.put("rows",rows);
            if(db==null)return out.toString();
            String index;
            switch(criterion){
                case "id":index="id";break;
                case "name":index="nameKey";break;
                case "set":index="setCode";break;
                case "collector":index="collectorKey";break;
                case "illustration":index="illustrationId";break;
                case "oracle":index="oracleId";break;
                case "setcollector":index="setCode=? AND collectorKey";break;
                default:out.put("error","Unknown index");return out.toString();
            }
            String cleaned=term==null?"":term.trim().toLowerCase(Locale.ROOT);
            String[] args;
            String where;
            if(criterion.equals("setcollector")){
                String[] pair=cleaned.split("#",2);
                if(pair.length!=2){out.put("error","Missing set or collector");return out.toString();}
                where="setCode=? AND collectorKey=?";args=pair;
            }else{where=index+"=?";args=new String[]{cleaned};}
            int limit=Math.max(1,Math.min(max,2001));
            int n=0;
            try(Cursor c=db.rawQuery("SELECT data FROM printings WHERE "+where+" LIMIT "+(limit+1),args)){
                while(c.moveToNext()){
                    if(n++>=limit)break;
                    rows.put(new JSONObject(c.getString(0)));
                }
            }
            out.put("complete",n<=limit);
        }catch(Exception e){
            try{out.put("error",e.getMessage());out.put("complete",false);}catch(Exception ignored){}
            Log.w(TAG,"Catalog lookup failed",e);
        }
        return out.toString();
    }
}
