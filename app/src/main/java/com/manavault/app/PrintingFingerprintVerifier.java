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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                int x=Math.round(card.getWidth()*.035f),y=Math.round(card.getHeight()*.49f);
                int w=Math.min(card.getWidth()-x,Math.round(card.getWidth()*.93f));
                int h=Math.min(card.getHeight()-y,Math.round(card.getHeight()*.17f));
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
            // Do NOT stop extra footer reads for a standalone set size such
            // as 185 or 269. That was the reason Aven Skirmisher and Catalog
            // needed manual selection although the set code was readable.
            if(raw.matches(".*[0-9]{1,5}\\s*[/]\\s*[0-9]{2,5}.*"))return true;
            // When the set code and a rarity/number are clearly together,
            // extra OCR is unnecessary. Ignore a bare numeric denominator.
            if(raw.matches(".*\\b[CMRU]\\s*0?[0-9]{2,5}\\s+[A-Z][A-Z0-9]{1,4}\\b.*"))return true;
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

    /** Compare the dedicated Scryfall set-icon silhouettes against multiple
     * locations on the physical type bar. Never wait on an image download from
     * this scan path; a symbol is supporting evidence and does not replace the
     * printed collector/set marks or card identity.
     */
    static JSONObject rankSymbols(File photo, JSONObject request) throws Exception {
        JSONObject out=new JSONObject();out.put("requestId",request.optString("requestId",""));
        JSONArray choices=request.optJSONArray("choices");
        if(choices==null||choices.length()<2||choices.length()>96){
            out.put("error","Unsupported expansion-symbol candidate count");return out;
        }
        Bitmap card=BitmapFactory.decodeFile(photo.getAbsolutePath());
        if(card==null){out.put("error","Captured card image unavailable");return out;}
        List<byte[]> observed=new ArrayList<>();
        try {
            // The printed symbol normally sits toward the right end of the
            // type line. Search a small bounded neighborhood for frames where
            // its placement shifts, rather than sampling the entire artwork.
            float[] xs={.82f,.86f,.90f};
            float[] ys={.545f,.575f,.605f};
            float[][] sizes={{.071f,.058f},{.086f,.068f}};
            for(float x:xs)for(float y:ys)for(float[] size:sizes){
                byte[] mask=readSymbolMask(card,x,y,size[0],size[1]);
                if(mask!=null)observed.add(mask);
            }
        }finally{card.recycle();}
        JSONArray ranked=new JSONArray();
        if(observed.isEmpty()){
            out.put("matches",ranked);out.put("failed",choices.length());
            out.put("attempted",choices.length());out.put("error","Set icon not distinguishable in captured type bar");
            return out;
        }
        Map<String,Double> scored=new LinkedHashMap<>();int unavailable=0;
        for(int i=0;i<choices.length();i++){
            JSONObject item=choices.optJSONObject(i);
            if(item==null){unavailable++;continue;}
            String set=item.optString("set","").toLowerCase(Locale.ROOT);
            if(scored.containsKey(set))continue;
            byte[] icon=BundledSetSymbols.icon(set);
            if(icon==null){unavailable++;continue;}
            double best=0;
            for(byte[] mask:observed)best=Math.max(best,SymbolShapeMatcher.score(icon,mask));
            scored.put(set,best);
        }
        List<Map.Entry<String,Double>> sorted=new ArrayList<>(scored.entrySet());
        sorted.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
        for(Map.Entry<String,Double> item:sorted){
            JSONObject row=new JSONObject();row.put("set",item.getKey());
            row.put("score",Math.round(item.getValue()*10000.)/10000.);ranked.put(row);
        }
        out.put("matches",ranked);out.put("failed",unavailable);
        out.put("attempted",scored.size());out.put("reference","scryfall-svg-icon-v3");
        out.put("atlasSets",BundledSetSymbols.count());
        return out;
    }

    /** A small centered symbol window. Photo grayscale is classified into ink
     * relative to the type-bar background, so rarity-colored icons remain usable.
     */
    private static byte[] readSymbolMask(Bitmap card,float centerX,float centerY,float width,float height){
        int w=Math.max(1,Math.round(card.getWidth()*width));
        int h=Math.max(1,Math.round(card.getHeight()*height));
        int x=Math.max(0,Math.min(card.getWidth()-w,Math.round(card.getWidth()*centerX-w/2f)));
        int y=Math.max(0,Math.min(card.getHeight()-h,Math.round(card.getHeight()*centerY-h/2f)));
        Bitmap part=null,scaled=null;
        try{
            part=Bitmap.createBitmap(card,x,y,w,h);
            scaled=Bitmap.createScaledBitmap(part,24,24,true);
            int[] gray=new int[576];
            for(int i=0;i<576;i++){
                int pixel=scaled.getPixel(i%24,i/24);
                gray[i]=(Color.red(pixel)*299+Color.green(pixel)*587+Color.blue(pixel)*114)/1000;
            }
            return SymbolShapeMatcher.extractInk(gray);
        }catch(Exception ignored){return null;}
        finally{if(scaled!=null)scaled.recycle();if(part!=null)part.recycle();}
    }

}
