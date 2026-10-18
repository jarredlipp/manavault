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

    /** A single bounded OCR pass over the rules text. This is invoked only
     * when title recognition needs corroboration, not for every accepted card.
     * It does not read a printing or use network resources. */
    static JSONObject readReferenceText(File photo, String requestId) throws Exception {
        JSONObject out=new JSONObject();out.put("requestId",requestId);
        Bitmap card=BitmapFactory.decodeFile(photo.getAbsolutePath());
        if(card==null){out.put("error","Captured card missing");return out;}
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            int x=Math.round(card.getWidth()*.07f), y=Math.round(card.getHeight()*.59f);
            int w=Math.min(card.getWidth()-x,Math.round(card.getWidth()*.86f));
            int h=Math.min(card.getHeight()-y,Math.round(card.getHeight()*.26f));
            Bitmap rules=Bitmap.createBitmap(card,x,y,w,h);
            try{
                Text result=Tasks.await(recognizer.process(InputImage.fromBitmap(rules,0)),950,TimeUnit.MILLISECONDS);
                StringBuilder content=new StringBuilder();
                for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                    if(content.length()>0)content.append(' ');
                    content.append(line.getText());
                }
                out.put("referenceText",content.substring(0,Math.min(content.length(),1100)));
            }finally{rules.recycle();}
        }catch(Exception e){out.put("error","Rules text could not be read quickly");}
        finally{recognizer.close();card.recycle();}
        return out;
    }

    static JSONObject readMarks(File photo, String requestId) throws Exception {
        return readMarks(photo,requestId,false,false,false);
    }
    static JSONObject readMarks(File photo, String requestId, boolean verifySplit) throws Exception {
        return readMarks(photo,requestId,verifySplit,false,false);
    }
    static JSONObject readMarks(File photo, String requestId, boolean verifySplit, boolean fast) throws Exception {
        return readMarks(photo,requestId,verifySplit,fast,false);
    }
    static JSONObject readMarks(File photo, String requestId, boolean verifySplit, boolean fast, boolean verifyToken) throws Exception {
        JSONObject out=new JSONObject();out.put("requestId",requestId);
        JSONArray lines=new JSONArray();out.put("lines",lines);
        Bitmap card=BitmapFactory.decodeFile(photo.getAbsolutePath());
        if(card==null){out.put("error","Captured card image unavailable");return out;}
        TextRecognizer reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        final long started=android.os.SystemClock.elapsedRealtime();
        try {
            // Token planeswalkers such as Jace use the same short OCR title as
            // non-token printings. Check the type bar even in fast mode when
            // that card's catalog has token and non-token possibilities.
            if(verifyToken){
                int x=Math.round(card.getWidth()*.035f),y=Math.round(card.getHeight()*.57f);
                int w=Math.min(card.getWidth()-x,Math.round(card.getWidth()*.93f));
                int h=Math.min(card.getHeight()-y,Math.round(card.getHeight()*.14f));
                Bitmap bar=Bitmap.createBitmap(card,x,y,w,h);
                try{readRegion(reader,bar,"type-bar","token-check",lines,out,fast?550:1100);}
                finally{bar.recycle();}
            }
            // For a split card, first corroborate BOTH photographed title
            // halves before spending time on the less reliable tiny footer.
            if(verifySplit){
                float[][] halves={{.015f,.012f,.48f,.27f},{.50f,.012f,.48f,.27f}};
                String[] labels={"split-title-left","split-title-right"};
                for(int i=0;i<halves.length;i++){
                    float[] r=halves[i];
                    int x=Math.round(card.getWidth()*r[0]),y=Math.round(card.getHeight()*r[1]);
                    int w=Math.min(card.getWidth()-x,Math.max(1,Math.round(card.getWidth()*r[2])));
                    int h=Math.min(card.getHeight()-y,Math.max(1,Math.round(card.getHeight()*r[3])));
                    Bitmap half=Bitmap.createBitmap(card,x,y,w,h);
                    Bitmap scaled=Bitmap.createScaledBitmap(half,w*2,h*2,true);
                    half.recycle();
                    try{readRegion(reader,scaled,labels[i],"split-check",lines,out,fast?1050:4000);}
                    finally{scaled.recycle();}
                }
            }
            // Keep the artwork/title out: copyright years and game rules are NOT
            // valid collector-number evidence. Old and new frames position the
            // small collector/expansion text differently near the bottom edge.
            // Several tight, overlapping footer regions are necessary because
            // collector text varies across modern, old, promo, and token frames.
            // The type bar is inspected separately to avoid confusing a Jace
            // token planeswalker with a normal planeswalker of the same name.
            // The two first crops isolate the collector/set row, not copyright
            // notices or power/toughness. They receive both color and enhanced OCR.
            // v5.12.1: the previous fast mode only read two very thin strips
            // (y=.925-.985). It frequently omitted the actual printed marks
            // on old frames and lowered-title/full-art treatments. Keep the
            // first two reads narrow and fast, then use one taller bottom-left
            // rescue crop if they produced no plausible collector number.
            String[] zones={"footer-left","footer-line","footer-deep","bottom-band",
                "bottom-left","bottom-right","type-bar"};
            float[][] regions={{.025f,.875f,.68f,.115f},{.018f,.930f,.88f,.065f},
                {.018f,.830f,.72f,.162f},{.018f,.910f,.96f,.085f},
                {.018f,.865f,.53f,.125f},{.48f,.87f,.50f,.12f},
                {.035f,.585f,.93f,.095f}};
            for(int zone=0;zone<zones.length;zone++){
                if(android.os.SystemClock.elapsedRealtime()-started>(fast?1500:6000))break;
                // The third crop is a fast rescue for older/shifted print
                // lines. Do not spend six OCR passes on every common card.
                if(fast && zone>2)break;
                if(fast && zone==2 && hasFooterIdentifier(lines))break;
                float[] region=regions[zone];
                int x=Math.round(card.getWidth()*region[0]);
                int y=Math.round(card.getHeight()*region[1]);
                int w=Math.max(1,Math.min(Math.round(card.getWidth()*region[2]),card.getWidth()-x));
                int h=Math.max(1,Math.min(Math.round(card.getHeight()*region[3]),card.getHeight()-y));
                Bitmap strip=Bitmap.createBitmap(card,x,y,w,h);
                int scale=Math.max(1,Math.min(4,Math.min(260/Math.max(1,h),2200/Math.max(1,w))));
                Bitmap enlarged=scale==1?strip:Bitmap.createScaledBitmap(strip,w*scale,h*scale,true);
                if(enlarged!=strip)strip.recycle();
                try {
                    readRegion(reader,enlarged,zones[zone],"color",lines,out,fast?850:4000);
                    // Contrast rescue only when plain OCR does not find a
                    // plausible footer mark. No network or reference-image I/O.
                    if(!hasFooterIdentifier(lines) &&
                            android.os.SystemClock.elapsedRealtime()-started<(fast?1050:5200)){
                        Bitmap enhanced=CardQuadDetector.enhanceTitle(enlarged);
                        try{readRegion(reader,enhanced,zones[zone],"contrast",lines,out,fast?850:4000);}
                        finally{enhanced.recycle();}
                    }
                }finally{enlarged.recycle();}
            }
        }finally{reader.close();card.recycle();}
        return out;
    }

    /** Avoid extra full-footer OCR when a plausible printing mark is already
     * visible. This only controls how many regions to read, NOT verification. */
    private static boolean hasFooterIdentifier(JSONArray lines){
        for(int i=0;i<lines.length();i++){
            JSONObject row=lines.optJSONObject(i);
            if(row==null)continue;
            String raw=row.optString("text","").toUpperCase(Locale.ROOT);
            // R 0345 DFT, 143/199 HOU, or C13 046; skip copyright years.
            if(raw.matches(".*(?:^|[^A-Z0-9])(?:[CMRUT]\\s*)?[0-9]{2,4}(?:\\s*[/]\\s*[0-9]{2,4})?(?:$|[^A-Z0-9]).*")
                    && !raw.matches(".*\\b(?:19|20)[0-9]{2}\\s+WIZARDS.*"))return true;
        }
        return false;
    }

    private static void readRegion(TextRecognizer reader, Bitmap image, String zone,
            String pass, JSONArray lines, JSONObject out, int timeoutMillis) throws Exception {
        try {
            Text words=Tasks.await(reader.process(InputImage.fromBitmap(image,0)),timeoutMillis,TimeUnit.MILLISECONDS);
            for(Text.TextBlock block:words.getTextBlocks())for(Text.Line line:block.getLines()){
                String value=line.getText().trim();
                if(value.isEmpty())continue;
                JSONObject item=new JSONObject();
                item.put("text",value);
                item.put("zone",zone);
                item.put("pass",pass);
                item.put("confidence",line.getConfidence());
                lines.put(item);
                if(zone.equals("type-bar") && value.toLowerCase(Locale.ROOT).contains("token"))
                    out.put("token",true);
            }
        }catch(Exception ignored){/* OCR uncertainty is not a printing identity. */}
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
        int maxSets=request.optBoolean("offline_only",false)?96:12;
        if(choices==null||choices.length()<2||choices.length()>maxSets){out.put("error","Unsupported expansion-symbol candidate count");return out;}
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
                    String set=item.optString("set","");
                    List<double[]> bundled=BundledSetSymbols.forSet(set);
                    // Never call the network from the live fast path.
                    if(bundled==null && request.optBoolean("offline_only",false))return null;
                    double score=0;
                    if(bundled!=null){
                        for(double[] expected:bundled)score=Math.max(score,similarity(scanned,expected));
                    }else{
                        String reference=item.optString("image_normal","");
                        score=similarity(scanned,referenceSymbol(reference));
                    }
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
        connection.setRequestProperty("User-Agent","ManaVault/5.11.1 (printing evidence)");
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
