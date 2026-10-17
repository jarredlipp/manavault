package com.manavault.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Auxiliary PRINTING evidence, not a substitute for an identified card title.
 * Reads the bottom collector/set text and compares printed set-symbol regions
 * with the same region in official Scryfall printing photographs.
 * Weak or indistinguishable evidence always goes to manual review.
 */
final class PrintingFingerprintVerifier {
    private PrintingFingerprintVerifier() {}

    static JSONObject readMarks(File photo, String requestId) throws Exception {
        JSONObject out=new JSONObject();out.put("requestId",requestId);
        JSONArray lines=new JSONArray();out.put("lines",lines);
        Bitmap card=BitmapFactory.decodeFile(photo.getAbsolutePath());
        if(card==null){out.put("error","Captured card image unavailable");return out;}
        TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            // Keep the artwork/title out: copyright years and game rules are NOT
            // valid collector-number evidence. Old and new frames position the
            // small collector/expansion text differently near the bottom edge.
            // Several tight, overlapping footer regions are necessary because
            // collector text varies across modern, old, promo, and token frames.
            // The type bar is inspected separately to avoid confusing a Jace
            // token planeswalker with a normal planeswalker of the same name.
            String[] zones={"bottom-left","bottom-band","bottom-left","bottom-right","bottom-band","type-bar"};
            float[][] regions={{.018f,.905f,.48f,.087f},
                {.018f,.920f,.96f,.073f},{.018f,.865f,.53f,.125f},
                {.48f,.87f,.50f,.12f},{.018f,.885f,.95f,.09f},
                {.035f,.585f,.93f,.095f}};
            for(int zone=0;zone<zones.length;zone++){
                float[] region=regions[zone];
                int x=Math.round(card.getWidth()*region[0]);
                int y=Math.round(card.getHeight()*region[1]);
                int w=Math.max(1,Math.min(Math.round(card.getWidth()*region[2]),card.getWidth()-x));
                int h=Math.max(1,Math.min(Math.round(card.getHeight()*region[3]),card.getHeight()-y));
                Bitmap strip=Bitmap.createBitmap(card,x,y,w,h);
                Bitmap enlarged=Bitmap.createScaledBitmap(strip,w*3,h*3,true);
                strip.recycle();
                try {
                    Text words=Tasks.await(reader.process(InputImage.fromBitmap(enlarged,0)),4,TimeUnit.SECONDS);
                    for(Text.TextBlock block:words.getTextBlocks())for(Text.Line line:block.getLines()){
                        String value=line.getText().trim();
                        if(value.isEmpty())continue;
                        JSONObject item=new JSONObject();
                        item.put("text",value);
                        item.put("zone",zones[zone]);
                        item.put("confidence",line.getConfidence());
                        lines.put(item);
                        if(zones[zone].equals("type-bar") && value.toLowerCase(Locale.ROOT).contains("token"))
                            out.put("token",true);
                    }
                }catch(Exception ignored){/* Missing OCR is never proof of another edition. */}
                finally{enlarged.recycle();}
            }
        }finally{reader.close();card.recycle();}
        return out;
    }

    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(4);
    private static final Map<String,double[]> CACHE=new LinkedHashMap<String,double[]>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,double[]> entry){return size()>64;}
    };
    /** Compare only the printed expansion-symbol region, never the card artwork.
     * A similarity is an uncalibrated score, NOT an identity probability.
     * Callers must require a unique set and an independently established name.
     */
    static JSONObject rankSymbols(File photo, JSONObject request) throws Exception {
        JSONObject out=new JSONObject();out.put("requestId",request.optString("requestId",""));
        JSONArray choices=request.optJSONArray("choices");
        if(choices==null||choices.length()<2||choices.length()>12){out.put("error","Unsupported expansion-symbol candidate count");return out;}
        Bitmap photoCard=BitmapFactory.decodeFile(photo.getAbsolutePath());
        if(photoCard==null){out.put("error","Captured card image unavailable");return out;}
        double[] scanned;
        try {scanned=symbolSignature(photoCard);}finally{photoCard.recycle();}
        List<Future<JSONObject>> jobs=new ArrayList<>();
        for(int i=0;i<choices.length();i++){
            final JSONObject item=choices.optJSONObject(i);
            jobs.add(NETWORK.submit(new Callable<JSONObject>(){
                @Override public JSONObject call() throws Exception {
                    if(item==null)return null;
                    String reference=item.optString("image_normal","");
                    double[] expected=referenceSymbol(reference);
                    double score=similarity(scanned,expected);
                    JSONObject row=new JSONObject();row.put("set",item.optString("set",""));
                    row.put("score",Math.round(score*10000.)/10000.);
                    return row;
                }
            }));
        }
        List<JSONObject> matches=new ArrayList<>();int failed=0;
        for(Future<JSONObject> job:jobs)try{
            JSONObject row=job.get(9,TimeUnit.SECONDS);
            if(row==null)failed++;else matches.add(row);
        }catch(Exception ex){failed++;job.cancel(true);}
        matches.sort((a,b)->Double.compare(b.optDouble("score",0),a.optDouble("score",0)));
        JSONArray ranked=new JSONArray();for(JSONObject match:matches)ranked.put(match);
        out.put("matches",ranked);out.put("failed",failed);out.put("attempted",choices.length());
        return out;
    }
    private static boolean safe(String path){
        try {URL url=new URL(path);
            return url.getProtocol().equals("https")&&url.getHost().equalsIgnoreCase("cards.scryfall.io")&&
                url.getPath().contains("/normal/");
        }catch(Exception e){return false;}
    }
    private static double[] referenceSymbol(String address) throws Exception {
        if(!safe(address))throw new SecurityException("Expected an official Scryfall normal card image");
        synchronized(CACHE){double[] cached=CACHE.get(address);if(cached!=null)return cached;}
        double[] saved=ReferenceFingerprintCache.get("symbol-v2",address);
        if(saved!=null){synchronized(CACHE){CACHE.put(address,saved);}return saved;}
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(1800);connection.setReadTimeout(2300);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","ManaVault/5.10.6 (printing evidence)");
        Bitmap reference=null;
        try{
            if(connection.getResponseCode()!=200||connection.getContentLengthLong()>4_000_000)
                throw new java.io.IOException("Expansion symbol reference unavailable");
            reference=BitmapFactory.decodeStream(connection.getInputStream());
            if(reference==null)throw new java.io.IOException("Unreadable printing reference");
            double[] signature=symbolSignature(reference);
            synchronized(CACHE){CACHE.put(address,signature);}
            ReferenceFingerprintCache.put("symbol-v2",address,signature);
            return signature;
        }finally{if(reference!=null)reference.recycle();connection.disconnect();}
    }
    /** Sample several type-bar placements. The preceding single y=.53 crop
     * missed modern/token/showcase symbols whenever the type bar shifted.
     * Only compare the SAME placement against the reference to avoid picking
     * up an unrelated artwork detail in a different location. */
    private static double[] symbolSignature(Bitmap card){
        float[] positions={.515f,.548f,.580f};
        double[] signatures=new double[positions.length*576];
        for(int zone=0;zone<positions.length;zone++){
            int x=Math.round(card.getWidth()*.82f),y=Math.round(card.getHeight()*positions[zone]);
            int w=Math.max(1,Math.min(Math.round(card.getWidth()*.16f),card.getWidth()-x));
            int h=Math.max(1,Math.min(Math.round(card.getHeight()*.075f),card.getHeight()-y));
            Bitmap roi=Bitmap.createBitmap(card,x,y,w,h);
            Bitmap scaled=Bitmap.createScaledBitmap(roi,24,24,true);
            roi.recycle();double average=0;
            for(int j=0;j<576;j++){
                int rgb=scaled.getPixel(j%24,j/24);
                double lum=.299*Color.red(rgb)+.587*Color.green(rgb)+.114*Color.blue(rgb);
                signatures[zone*576+j]=lum;average+=lum;
            }
            scaled.recycle();average/=576;
            double sum=0;
            for(int j=0;j<576;j++){
                int k=zone*576+j;
                signatures[k]-=average;sum+=signatures[k]*signatures[k];
            }
            double rms=Math.sqrt(sum);
            if(rms>1e-6)for(int j=0;j<576;j++)signatures[zone*576+j]/=rms;
        }
        return signatures;
    }
    private static double similarity(double[] a,double[] b){
        if(a==null||b==null||a.length!=1728||b.length!=1728)return 0;
        double best=0;
        for(int region=0;region<3;region++){
            double dot=0;
            for(int i=0;i<576;i++)dot+=a[region*576+i]*b[region*576+i];
            best=Math.max(best,Math.max(0,Math.min(1,(dot+1)/2)));
        }
        return best;
    }

}
