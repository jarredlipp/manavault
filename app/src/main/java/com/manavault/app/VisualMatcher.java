package com.manavault.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Independent artwork matcher over known, permission-compatible Scryfall art crops.
 * A photograph only supports a *candidate* name: it cannot prove printing, foil or condition.
 * A bundled visual-index.json (made by tools/build_visual_index.py) is optional; learned
 * references grow after the user confirms a printing, without private provider APIs.
 */
final class VisualMatcher {
    private static final String TAG="ManaVaultVisual";
    private static final int MAX_BUNDLED=60000;
    private static final int MAX_LEARNED=800;
    private final File learnedFile;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private final ArrayList<Entry> learned=new ArrayList<>();
    private final java.util.LinkedHashMap<Long,Match> cache=new java.util.LinkedHashMap<Long,Match>(180,.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long,Match> eldest){return size()>160;}
    };

    static final class Match {
        final String name,id;
        final float similarity,margin;
        final int referenceCount;
        Match(String n,String i,float s,float m,int count){name=n;id=i;similarity=s;margin=m;referenceCount=count;}
        /** Similarity of compressed color thumbnails is not calibrated probability. */
        boolean usable(){
            // A tiny seed catalog covers only a fraction of MTG. Do not let
            // a vaguely similar image override readable OCR or invent a name.
            float minimum=referenceCount<100?.975f:referenceCount<1000?.945f:.91f;
            float separation=referenceCount<100?.12f:referenceCount<1000?.08f:.055f;
            return similarity>=minimum&&margin>=separation&&name!=null&&!name.isEmpty();
        }
    }
    private static final class Entry {
        String name,id; byte[] f;
        Entry(String name,String id,byte[] values){this.name=name;this.id=id;this.f=values;}
    }
    VisualMatcher(Context context){
        learnedFile=new File(context.getFilesDir(),"learned-artwork-v59.json");
        try(InputStream in=context.getAssets().open("visual-index.json")){
            byte[] bytes=readLimited(in,28_000_000);
            JSONArray cards=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).optJSONArray("cards");
            load(cards,entries,MAX_BUNDLED);
        }catch(java.io.IOException ignored){Log.i(TAG,"No bundled artwork index: OCR fallback and learned references active");}
        catch(Exception e){Log.w(TAG,"Invalid bundled visual index; OCR fallback active",e);}
        try{
            if(learnedFile.isFile()){
                try(InputStream in=new java.io.FileInputStream(learnedFile)){
                    JSONArray cards=new JSONArray(new String(readLimited(in,1_400_000),java.nio.charset.StandardCharsets.UTF_8));
                    load(cards,learned,MAX_LEARNED);
                }
            }
        }catch(Exception e){Log.w(TAG,"Unable to read learned artwork",e);}
        entries.addAll(learned);
    }
    int count(){return entries.size();}
    private static byte[] readLimited(InputStream in,int limit) throws java.io.IOException {
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(output.size()+n>limit)throw new java.io.IOException("Visual index exceeds supported size");output.write(buf,0,n);}
        return output.toByteArray();
    }
    private static void load(JSONArray a,List<Entry> target,int max){
        if(a==null)return;
        for(int i=0;i<a.length()&&target.size()<max;i++){
            JSONObject j=a.optJSONObject(i);if(j==null)continue;
            String name=j.optString("name", ""),id=j.optString("id", "");
            JSONArray f=j.optJSONArray("f");if(name.isEmpty()||f==null||f.length()!=192)continue;
            byte[] b=new byte[192];for(int k=0;k<192;k++)b[k]=(byte)Math.max(0,Math.min(255,f.optInt(k)));
            target.add(new Entry(name,id,b));
        }
    }
    static byte[] fingerprint(Bitmap image){
        Bitmap scaled=Bitmap.createScaledBitmap(image,8,8,true);
        byte[] f=new byte[192];int i=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            int c=scaled.getPixel(x,y);f[i++]=(byte)Color.red(c);f[i++]=(byte)Color.green(c);f[i++]=(byte)Color.blue(c);
        }
        if(scaled!=image)scaled.recycle();return f;
    }
    /** Standard MTG portrait art window; title and text box are intentionally excluded. */
    static Bitmap artRegion(Bitmap card){
        int x=Math.round(card.getWidth()*.09f),y=Math.round(card.getHeight()*.15f);
        int w=Math.max(1,Math.round(card.getWidth()*.82f)),h=Math.max(1,Math.round(card.getHeight()*.34f));
        return Bitmap.createBitmap(card,x,y,Math.min(w,card.getWidth()-x),Math.min(h,card.getHeight()-y));
    }
    static long quickHash(Bitmap card){
        Bitmap crop=artRegion(card),scaled=Bitmap.createScaledBitmap(crop,9,8,true);long hash=0L;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            int a=scaled.getPixel(x,y),b=scaled.getPixel(x+1,y);
            int la=(Color.red(a)*30+Color.green(a)*59+Color.blue(a)*11)/100;
            int lb=(Color.red(b)*30+Color.green(b)*59+Color.blue(b)*11)/100;
            if(la>lb)hash|=1L<<(y*8+x);
        }
        scaled.recycle();crop.recycle();return hash;
    }
    synchronized Match match(Bitmap card){
        if(entries.isEmpty())return null;
        long key=quickHash(card);
        if(cache.containsKey(key))return cache.get(key);
        Bitmap crop=artRegion(card);
        byte[] f=fingerprint(crop);crop.recycle();
        float best=-1,second=-1;Entry winner=null;
        // Exclude nearly identical printings of the same card from margin competition.
        for(Entry entry:entries){
            int delta=0;for(int j=0;j<192;j++)delta+=Math.abs((f[j]&255)-(entry.f[j]&255));
            float sim=1f-delta/(192f*255f);
            if(sim>best){if(winner!=null&&!winner.name.equalsIgnoreCase(entry.name))second=Math.max(second,best);best=sim;winner=entry;}
            else if(winner!=null&&!winner.name.equalsIgnoreCase(entry.name))second=Math.max(second,sim);
        }
        if(winner==null)return null;
        Match result=new Match(winner.name,winner.id,best,best-Math.max(0,second),entries.size());
        cache.put(key,result);
        return result;
    }
    /** Cache only explicitly confirmed artwork references; no user identifiers or tokens. */
    synchronized boolean learn(String name,String id,String referenceUrl){
        if(name==null||name.trim().isEmpty()||id==null||id.trim().isEmpty())return false;
        if(referenceUrl==null)return false;
        try{
            URL uri=new URL(referenceUrl);
            String host=uri.getHost().toLowerCase(Locale.ROOT);
            if(!uri.getProtocol().equals("https")||!(host.equals("cards.scryfall.io")||host.equals("scryfall.com")))return false;
            for(Entry e:entries)if(e.id.equals(id))return true;
            HttpURLConnection conn=(HttpURLConnection)uri.openConnection();
            conn.setConnectTimeout(2800);conn.setReadTimeout(4500);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","ManaVault/5.9 (collection artwork reference)");
            Bitmap bitmap=null;
            try{
                if(conn.getResponseCode()!=200||conn.getContentLengthLong()>3_000_000)return false;
                try(InputStream in=conn.getInputStream()) {bitmap=BitmapFactory.decodeStream(in);}
                if(bitmap==null)return false;
                Entry entry=new Entry(name,id,fingerprint(bitmap));entries.add(entry);learned.add(entry);cache.clear();
                while(learned.size()>MAX_LEARNED){Entry removed=learned.remove(0);entries.remove(removed);}
                JSONArray arr=new JSONArray();
                for(Entry item:learned){JSONObject o=new JSONObject();o.put("name",item.name);o.put("id",item.id);
                    JSONArray f=new JSONArray();for(byte b:item.f)f.put(b&255);o.put("f",f);arr.put(o);}
                File temp=new File(learnedFile.getParentFile(),"learned-artwork-v59.tmp");
                try(FileOutputStream out=new FileOutputStream(temp)){out.write(arr.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                if(!temp.renameTo(learnedFile))Log.w(TAG,"Unable to persist updated artwork cache");
                return true;
            }finally{if(bitmap!=null)bitmap.recycle();conn.disconnect();}
        }catch(Exception e){Log.w(TAG,"Reference artwork unavailable",e);return false;}
    }
}
