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
    private static final String FILE="printing-catalog-5120.sqlite";
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
                if(!c.moveToFirst()||c.getInt(0)<100000)return false;
                try(Cursor r=d.rawQuery("SELECT COUNT(*) FROM reference_fts",null)){
                    return r.moveToFirst() && r.getInt(0)>=100000;
                }
            }
        }catch(Exception e){return false;}
    }
    static String status(){return state;}
    static String version(){return sourceStamp;}
    /** Pre-indexed offline title/type/rules search, never a printing assertion.
     * FTS is deliberately bounded and ignores operators supplied by OCR. */
    static String searchReference(String raw, int limit){
        JSONObject out=new JSONObject();JSONArray hits=new JSONArray();
        try{
            SQLiteDatabase db=database;
            out.put("ready",db!=null);out.put("rows",hits);out.put("complete",db!=null);
            if(db==null)return out.toString();
            String cleaned=java.text.Normalizer.normalize(raw==null?"":raw,java.text.Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+"," ");
            String[] parts=cleaned.split("\\s+");
            java.util.LinkedHashSet<String> terms=new java.util.LinkedHashSet<>();
            java.util.Set<String> ignore=new java.util.HashSet<>(java.util.Arrays.asList(
                "the","and","you","your","target","when","this","that","card","creature","player","with","from","into","may","one","all","then","have","are","for","its","who","can","not","until","end","turn","owner","they","their","each","control","battlefield","enters","library","graveyard","hand","cast","mana","cost"));
            for(String part:parts){if(part.length()>=4 && part.length()<=24 && !ignore.contains(part))terms.add(part);if(terms.size()>=5)break;}
            if(terms.size()<2){out.put("error","Not enough distinctive reference text");return out.toString();}
            StringBuilder fts=new StringBuilder();int k=0;
            for(String t:terms){if(k++>0)fts.append(" AND ");fts.append('"').append(t).append('"');}
            int max=Math.max(1,Math.min(limit,25));
            // Search by rules text and printed title/type. Dedupe repeat editions
            // by normalized card name, but do not infer a specific printing.
            // FTS4 works on Android API 26+, unlike FTS5 which is not
            // universally bundled on older Android system SQLite builds.
            String sql="SELECT p.data FROM reference_fts JOIN printings p ON " +
                "p.id=reference_fts.printingId WHERE reference_fts MATCH ? " +
                "GROUP BY p.nameKey LIMIT 60";
            java.util.HashSet<String> names=new java.util.HashSet<>();
            try(Cursor c=db.rawQuery(sql,new String[]{fts.toString()})){
                while(c.moveToNext() && hits.length()<max){
                    JSONObject card=new JSONObject(c.getString(0));
                    String key=card.optString("nameKey",card.optString("name",""));
                    if(names.add(key)){
                        hits.put(card);
                    }
                }
            }
        }catch(Exception e){try{out.put("error",e.getMessage());}catch(Exception ignored){}Log.w(TAG,"Reference search failed",e);}
        return out.toString();
    }

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
