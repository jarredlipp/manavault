package com.manavault.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Advisory dHash comparison: not definitive evidence of a card printing. */
final class ArtworkVerifier {
    static final class Result {
        final double similarity;
        final String note;
        Result(double similarity,String note){this.similarity=similarity;this.note=note;}
    }
    static Result compare(File scannedFile,String referenceUrl) throws Exception {
        URL url=new URL(referenceUrl);
        String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
        if(!url.getProtocol().equals("https") || !(host.equals("cards.scryfall.io") || host.equals("scryfall.com")))
            throw new SecurityException("Only Scryfall HTTPS artwork references are supported");
        Bitmap card=BitmapFactory.decodeFile(scannedFile.getAbsolutePath());
        if(card==null)throw new IllegalArgumentException("The scanned card preview is missing");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(3200);connection.setReadTimeout(4000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","ManaVault/5.8 (personal collection scanner)");
        Bitmap ref=null;
        try{
            if(connection.getResponseCode()!=200)throw new java.io.IOException("Artwork HTTP "+connection.getResponseCode());
            if(connection.getContentLengthLong()>3_000_000)throw new java.io.IOException("Artwork too large");
            ref=BitmapFactory.decodeStream(connection.getInputStream());
            if(ref==null)throw new java.io.IOException("Artwork image unreadable");
            // Compare the art portion of the warped photographed card with art_crop,
            // not the complete card (which includes text and set-specific frame).
            Bitmap region=Bitmap.createBitmap(card,Math.round(card.getWidth()*.085f),
                    Math.round(card.getHeight()*.165f),Math.round(card.getWidth()*.83f),
                    Math.round(card.getHeight()*.31f));
            long a=hash(region),b=hash(ref);
            region.recycle();
            int difference=Long.bitCount(a^b);
            double similarity=1.0-difference/64.0;
            return new Result(similarity,"Artwork similarity "+Math.round(similarity*100)+"% (advisory; confirm edition manually)");
        }finally{card.recycle();if(ref!=null)ref.recycle();connection.disconnect();}
    }
    private static long hash(Bitmap image){
        Bitmap scaled=Bitmap.createScaledBitmap(image,9,8,true);
        long result=0;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            int a=scaled.getPixel(x,y),b=scaled.getPixel(x+1,y);
            int l=(android.graphics.Color.red(a)*30+android.graphics.Color.green(a)*59+android.graphics.Color.blue(a)*11)/100;
            int r=(android.graphics.Color.red(b)*30+android.graphics.Color.green(b)*59+android.graphics.Color.blue(b)*11)/100;
            if(l>r)result|=1L<<(y*8+x);
        }
        scaled.recycle();return result;
    }
    // Candidate-specific art comparisons work even with an empty bundled art index.
    // The app submits only Scryfall artwork URLs for one already identified name.
    // Similarity is not a probability, and repeated art cannot identify the printing.
    private static final ExecutorService REF_POOL=Executors.newFixedThreadPool(4);
    private static final LinkedHashMap<String,double[]> referenceCache=new LinkedHashMap<String,double[]>(130,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,double[]> eldest){return size()>120;}
    };
    private static double[] readReference(String address) throws Exception {
        if(!validReference(address))throw new SecurityException("Not a Scryfall art_crop URL");
        synchronized(referenceCache){double[] cached=referenceCache.get(address);if(cached!=null)return cached;}
        HttpURLConnection conn=null;Bitmap art=null;
        try{
            conn=(HttpURLConnection)new URL(address).openConnection();
            conn.setConnectTimeout(1300);conn.setReadTimeout(1900);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent","ManaVault/5.10 (printing image verification)");
            if(conn.getResponseCode()!=200||conn.getContentLengthLong()>3_000_000)
                throw new java.io.IOException("Artwork unavailable");
            try(java.io.InputStream input=conn.getInputStream()){
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=2;
                art=BitmapFactory.decodeStream(input,null,options);
            }
            if(art==null)throw new java.io.IOException("Unreadable artwork");
            double[] sig=signature(art);
            synchronized(referenceCache){referenceCache.put(address,sig);}
            return sig;
        }finally{if(art!=null)art.recycle();if(conn!=null)conn.disconnect();}
    }
    static JSONObject rank(File scannedFile, JSONObject request) throws Exception {
        JSONObject result=new JSONObject();
        result.put("requestId",request.optString("requestId",""));
        JSONArray choices=request.optJSONArray("choices");
        if(choices==null||choices.length()<1||choices.length()>12){
            result.put("error","Unsupported artwork comparison count");return result;
        }
        Bitmap scanned=BitmapFactory.decodeFile(scannedFile.getAbsolutePath());
        if(scanned==null){result.put("error","Captured image unavailable");return result;}
        Bitmap crop=null;
        try{
            crop=Bitmap.createBitmap(scanned,Math.round(scanned.getWidth()*.09f),
                Math.round(scanned.getHeight()*.15f),Math.round(scanned.getWidth()*.82f),
                Math.round(scanned.getHeight()*.34f));
            final double[] photographed=signature(crop);
            List<Future<JSONObject>> tasks=new ArrayList<>();
            for(int i=0;i<choices.length();i++){
                final JSONObject item=choices.optJSONObject(i);
                tasks.add(REF_POOL.submit(()->{
                    if(item==null)return null;
                    try{
                        double score=similarity(photographed,readReference(item.optString("art_url","")));
                        JSONObject candidate=new JSONObject();
                        candidate.put("id",item.optString("id",""));
                        candidate.put("score",Math.round(score*10000)/10000.0);
                        candidate.put("illustration_id",item.optString("illustration_id",""));
                        return candidate;
                    }catch(Exception e){return null;}
                }));
            }
            ArrayList<JSONObject> sorted=new ArrayList<>();int failures=0;
            for(Future<JSONObject> task:tasks){
                try{JSONObject candidate=task.get(4,TimeUnit.SECONDS);
                    if(candidate==null)failures++;else sorted.add(candidate);
                }catch(Exception e){failures++;task.cancel(true);}
            }
            sorted.sort((a,b)->Double.compare(b.optDouble("score",0),a.optDouble("score",0)));
            JSONArray matches=new JSONArray();for(JSONObject x:sorted)matches.put(x);
            result.put("matches",matches);
            result.put("attempted",choices.length());result.put("failed",failures);
            return result;
        }finally{if(crop!=null)crop.recycle();scanned.recycle();}
    }
    private static boolean validReference(String url){
        try{
            URL address=new URL(url);
            return "https".equals(address.getProtocol())&&"cards.scryfall.io".equalsIgnoreCase(address.getHost());
        }catch(Exception e){return false;}
    }
    /** 24x24 grayscale signature normalized against uniform lighting and tint. */
    private static double[] signature(Bitmap bitmap){
        Bitmap small=Bitmap.createScaledBitmap(bitmap,24,24,true);
        double[] pixels=new double[576];double mean=0;
        for(int y=0;y<24;y++)for(int x=0;x<24;x++){
            int rgb=small.getPixel(x,y);
            double v=.299*Color.red(rgb)+.587*Color.green(rgb)+.114*Color.blue(rgb);
            pixels[y*24+x]=v;mean+=v;
        }
        mean/=pixels.length;
        double norm=0;
        for(int i=0;i<pixels.length;i++){pixels[i]-=mean;norm+=pixels[i]*pixels[i];}
        norm=Math.sqrt(norm);
        if(norm>1e-6)for(int i=0;i<pixels.length;i++)pixels[i]/=norm;
        if(small!=bitmap)small.recycle();
        return pixels;
    }
    private static double similarity(double[] a,double[] b){
        double dot=0;
        for(int i=0;i<a.length;i++)dot+=a[i]*b[i];
        return Math.max(0,Math.min(1,(dot+1.0)/2.0));
    }

}
