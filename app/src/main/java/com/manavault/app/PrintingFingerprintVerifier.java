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
        return readMarks(photo,requestId,verifySplit,fast,verifyToken,null);
    }
    static JSONObject readMarks(File photo,String requestId,boolean verifySplit,boolean fast,
            boolean verifyToken,JSONArray expectedCollectors) throws Exception {
        return readMarks(photo,requestId,verifySplit,fast,verifyToken,expectedCollectors,false);
    }
    static JSONObject readMarks(File photo,String requestId,boolean verifySplit,boolean fast,
            boolean verifyToken,JSONArray expectedCollectors,boolean footerFirst) throws Exception {
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
            // v5.12.33: true split cards have TWO independently printed
            // title panels. Portrait photos show stacked sideways panels;
            // scanner-rotated landscape photos show left/right panels. The
            // former must be rotated before OCR; the old left/right unrotated
            // probes on a portrait photo mostly read RULES TEXT instead.
            if(verifySplit){
                float[][] panels=SplitCardLayoutPolicy.panels(card.getWidth(),card.getHeight());
                String[] labels=SplitCardLayoutPolicy.zones(card.getWidth(),card.getHeight());
                int degrees=SplitCardLayoutPolicy.titleRotation(card.getWidth(),card.getHeight());
                for(int i=0;i<panels.length;i++){
                    float[] r=panels[i];
                    int x=Math.round(card.getWidth()*r[0]),y=Math.round(card.getHeight()*r[1]);
                    int w=Math.max(1,Math.min(card.getWidth()-x,Math.round(card.getWidth()*r[2])));
                    int h=Math.max(1,Math.min(card.getHeight()-y,Math.round(card.getHeight()*r[3])));
                    Bitmap half=Bitmap.createBitmap(card,x,y,w,h);
                    Bitmap upright=half;
                    if(degrees!=0){
                        android.graphics.Matrix rotation=new android.graphics.Matrix();
                        rotation.postRotate(degrees);
                        upright=Bitmap.createBitmap(half,0,0,w,h,rotation,true);
                        half.recycle();
                    }
                    // Once rotated, OCR only the TOP of each sub-card; the
                    // right/lower rules area must never supply an exact name.
                    int titleHeight=Math.max(1,Math.round(upright.getHeight()*.34f));
                    Bitmap title=Bitmap.createBitmap(upright,0,0,upright.getWidth(),titleHeight);
                    if(upright!=half || degrees==0)upright.recycle();
                    Bitmap scaled=Bitmap.createScaledBitmap(title,title.getWidth()*2,title.getHeight()*2,true);
                    title.recycle();
                    try{readRegion(reader,scaled,labels[i],"split-rotated-title",lines,out,fast?1050:1800);}
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
            // If title OCR is uncertain, spend one limited, high-contrast
            // footer pass BEFORE querying by guessed name. This must include
            // the tiny set + collector row (e.g. DFT R 0345, FIN U 0214,
            // LCI U 0303). This never claims a title or a printing by itself.
            if(footerFirst && !hasFooterIdentifier(lines) &&
                    android.os.SystemClock.elapsedRealtime()-started<(fast?1850:5600)){
                int x=Math.round(card.getWidth()*.013f),y=Math.round(card.getHeight()*.91f);
                int w=Math.max(1,Math.min(card.getWidth()-x,Math.round(card.getWidth()*.78f)));
                int h=Math.max(1,Math.min(card.getHeight()-y,Math.round(card.getHeight()*.086f)));
                Bitmap strip=Bitmap.createBitmap(card,x,y,w,h);
                Bitmap scaled=Bitmap.createScaledBitmap(strip,w*4,h*4,true);
                strip.recycle();
                try{
                    Bitmap enhanced=CardQuadDetector.enhanceTitle(scaled);
                    try{readRegion(reader,enhanced,"footer-deep","footer-first-contrast",lines,out,fast?1050:1600);}
                    finally{enhanced.recycle();}
                }finally{scaled.recycle();}
                out.put("footerFirstRescueAttempted",true);
            }
            // Exact-title printings give us a small set of POSSIBLE collector
            // numbers. If rapid OCR missed all of them, spend one additional
            // focused read on the lower-left / old-frame footer. A recognized
            // number remains just OCR evidence: the JS resolver must still
            // check unique printing, set, contradictions and title confidence.
            if(expectedCollectors!=null && expectedCollectors.length()>0 &&
                    expectedCollectors.length()<=256 &&
                    android.os.SystemClock.elapsedRealtime()-started<1750){
                java.util.Set<String> expected=new java.util.HashSet<>();
                for(int j=0;j<expectedCollectors.length();j++){
                    String normalized=CollectorOcrPolicy.normalize(expectedCollectors.optString(j,""));
                    if(!normalized.isEmpty())expected.add(normalized);
                }
                boolean found=false;
                for(int j=0;j<lines.length() && !found;j++){
                    JSONObject line=lines.optJSONObject(j);
                    if(line!=null && CollectorOcrPolicy.hasCandidateNumber(line.optString("text",""),expected))
                        found=true;
                }
                if(!found && !expected.isEmpty()){
                    int left=Math.round(card.getWidth()*.015f);
                    int top=Math.round(card.getHeight()*.835f);
                    int width=Math.max(1,Math.min(card.getWidth()-left,Math.round(card.getWidth()*.72f)));
                    int height=Math.max(1,Math.min(card.getHeight()-top,Math.round(card.getHeight()*.155f)));
                    Bitmap footer=Bitmap.createBitmap(card,left,top,width,height);
                    Bitmap scaled=Bitmap.createScaledBitmap(footer,width*3,height*3,true);
                    footer.recycle();
                    try{
                        // Adaptive contrast improves small white text printed
                        // directly over full-art and extended-art backgrounds.
                        Bitmap contrast=CardQuadDetector.enhanceTitle(scaled);
                        try{readRegion(reader,contrast,"footer-deep","candidate-focused",lines,out,fast?1000:1400);}
                        finally{contrast.recycle();}
                    }finally{scaled.recycle();}
                    out.put("collectorRescueAttempted",true);
                }
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
            // The type-line icon moves on older frames and premium card
            // treatments. Sample nearby sizes and centers; confidence is
            // still evaluated against every plausible competing set icon.
            // v5.12.23: older, retro, and specialty MTG frames position the
            // expansion logo lower than the modern type line.  The previous
            // search stopped at 62.5% of card height and could miss Tempest,
            // Invasion, Seventh Edition and BRR retro artifact marks entirely.
            // Still search ONLY the right-hand type-line neighborhood, not
            // arbitrary art pixels; score all plausible sets against the same
            // crop collection and require a clearly separated winning shape.
            // Alternate, borderless and extended-art frames may move the
            // logo down or slightly farther left than a regular type line.
            // Keep scan time bounded: fewer crop windows, with an extra
            // normalized form of each window for shifted premium-frame icons.
            float[] xs={.735f,.805f,.875f,.94f};
            float[] ys={.49f,.545f,.60f,.655f,.71f,.765f};
            float[][] sizes={{.065f,.052f},{.085f,.072f}};
            for(float x:xs)for(float y:ys)for(float[] size:sizes){
                byte[] mask=readSymbolMask(card,x,y,size[0],size[1]);
                if(mask!=null){
                    observed.add(mask);
                    byte[] normalized=SymbolShapeMatcher.normalizeInk(mask);
                    if(normalized!=null)observed.add(normalized);
                }
            }
        }finally{card.recycle();}
        JSONArray ranked=new JSONArray();
        if(observed.isEmpty()){
            out.put("matches",ranked);out.put("failed",choices.length());
            out.put("attempted",choices.length());out.put("error","Set icon not distinguishable in captured type bar");
            return out;
        }
        Map<String,Double> scored=new LinkedHashMap<>();
        Map<String,byte[]> templates=new LinkedHashMap<>();int unavailable=0;
        for(int i=0;i<choices.length();i++){
            JSONObject item=choices.optJSONObject(i);
            if(item==null){unavailable++;continue;}
            String set=item.optString("set","").toLowerCase(Locale.ROOT);
            if(scored.containsKey(set))continue;
            byte[] icon=BundledSetSymbols.icon(set);
            if(icon==null){unavailable++;continue;}
            double best=0;
            for(byte[] mask:observed)best=Math.max(best,SymbolShapeMatcher.score(icon,mask));
            scored.put(set,best);templates.put(set,icon);
        }
        List<Map.Entry<String,Double>> sorted=new ArrayList<>(scored.entrySet());
        sorted.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
        for(Map.Entry<String,Double> item:sorted){
            JSONObject row=new JSONObject();row.put("set",item.getKey());
            row.put("score",Math.round(item.getValue()*10000.)/10000.);ranked.put(row);
        }
        // A contender scoring well on another unrelated textured crop must
        // not be treated as proof that the photographed set icon is distinct.
        // Compare BOTH candidate silhouettes within the SAME crop that gave
        // the top set its highest score. If its peer is too close in that
        // crop, the web resolver treats the symbol as ambiguous.
        double colocated=0;boolean coLocatedAmbiguous=true;
        if(sorted.size()>=2){
            String leader=sorted.get(0).getKey();double strongest=-1;
            byte[] winnerCrop=null;
            for(byte[] mask:observed){
                double quality=SymbolShapeMatcher.score(templates.get(leader),mask);
                if(quality>strongest){strongest=quality;winnerCrop=mask;}
            }
            if(winnerCrop!=null){
                double second=0;
                for(Map.Entry<String,byte[]> candidate:templates.entrySet()){
                    if(candidate.getKey().equals(leader))continue;
                    second=Math.max(second,SymbolShapeMatcher.score(candidate.getValue(),winnerCrop));
                }
                colocated=Math.max(0,strongest-second);
                coLocatedAmbiguous=strongest<.82 || colocated<.085;
            }
        }
        out.put("coLocatedMargin",Math.round(colocated*10000.)/10000.);
        out.put("coLocatedAmbiguous",coLocatedAmbiguous);
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
