package com.manavault.app;

import java.util.Arrays;

/** Pure-Java, allocation-bounded expansion-icon shape comparator.
 * Scores describe visual resemblance, NOT probabilities or verified printings.
 */
final class SymbolShapeMatcher {
    private SymbolShapeMatcher() {}
    static final int SIDE=24;
    static final int SIZE=SIDE*SIDE;

    /** Infer foreground ink from a photographed, resized 24x24 symbol crop.
     * The type bar may be pale, dark, gold or silver, so use contrast against
     * the crop's perimeter rather than assuming the ink is black.
     */
    static byte[] extractInk(int[] gray) {
        if(gray==null||gray.length!=SIZE)return null;
        int[] border=new int[8*SIDE];int count=0;
        for(int y=0;y<SIDE;y++)for(int x=0;x<SIDE;x++)
            if(x<2||x>=SIDE-2||y<2||y>=SIDE-2)border[count++]=gray[y*SIDE+x];
        Arrays.sort(border,0,count);
        int bg=border[count/2];
        int[] delta=new int[SIZE];int largest=0;
        for(int i=0;i<SIZE;i++){
            delta[i]=Math.abs(gray[i]-bg);
            largest=Math.max(largest,delta[i]);
        }
        if(largest<38)return null; // an indistinct symbol cannot verify a printing
        int threshold=Math.max(27,(int)(largest*.34));
        byte[] mask=new byte[SIZE];int pixels=0;
        for(int i=0;i<SIZE;i++)if(delta[i]>=threshold){mask[i]=(byte)255;pixels++;}
        // A huge textured patch or near-empty frame detail isn't a symbol.
        if(pixels<28||pixels>SIZE*.67)return null;
        // The downloaded Scryfall SVG is fitted to a centered 20x20 box.
        // Physical symbols vary in size/position inside the sampled type bar.
        // rankSymbols also tries a normalized mask when an icon is shifted
        // within the sampled region. Preserve this raw mask for small icons.
        return mask;
    }

    /** Scale and center a foreground glyph without changing its shape.
     * Deliberately reject crops dominated by a frame line or single stroke. */
    static byte[] normalizeInk(byte[] source){
        if(source==null||source.length!=SIZE)return null;
        int minX=SIDE,minY=SIDE,maxX=-1,maxY=-1;
        for(int y=0;y<SIDE;y++)for(int x=0;x<SIDE;x++){
            if((source[y*SIDE+x]&255)<100)continue;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        int w=maxX-minX+1,h=maxY-minY+1;
        if(w<4||h<4)return null;
        double scale=Math.min(20.0/w,20.0/h);
        int targetW=Math.max(1,(int)Math.round(w*scale));
        int targetH=Math.max(1,(int)Math.round(h*scale));
        int left=(SIDE-targetW)/2,top=(SIDE-targetH)/2;
        byte[] output=new byte[SIZE];
        for(int y=0;y<targetH;y++)for(int x=0;x<targetW;x++){
            int srcX=minX+Math.min(w-1,(int)((x+.5)*w/targetW));
            int srcY=minY+Math.min(h-1,(int)((y+.5)*h/targetH));
            output[(top+y)*SIDE+left+x]=source[srcY*SIDE+srcX];
        }
        return output;
    }

    static double score(byte[] template, byte[] observed) {
        if(template==null||observed==null||template.length!=SIZE||observed.length!=SIZE)return 0;
        int overlap=0, expected=0, actual=0;
        for(int i=0;i<SIZE;i++){
            boolean a=(template[i]&255)>=100,b=(observed[i]&255)>=100;
            if(a)expected++;
            if(b)actual++;
            if(a&&b)overlap++;
        }
        if(expected<16||actual<25||actual>SIZE*.7)return 0;
        // Dice overlap plus a small allowance for one-pixel perspective/OCR
        // noise. The bounded neighborhood also prevents a blank patch scoring.
        int tolerant=0;
        for(int y=1;y<SIDE-1;y++)for(int x=1;x<SIDE-1;x++){
            int at=y*SIDE+x;
            if((template[at]&255)<100)continue;
            boolean match=false;
            for(int oy=-1;oy<=1&&!match;oy++)for(int ox=-1;ox<=1;ox++)
                if((observed[at+oy*SIDE+ox]&255)>=100){match=true;break;}
            if(match)tolerant++;
        }
        double dice=2.0*overlap/(expected+actual);
        double tolerance=Math.min(1.0,(double)tolerant/expected);
        double sizePenalty=Math.min(expected,actual)/(double)Math.max(expected,actual);
        return Math.max(0,Math.min(1, (.77*dice+.23*tolerance)*Math.sqrt(sizePenalty)));
    }
}
