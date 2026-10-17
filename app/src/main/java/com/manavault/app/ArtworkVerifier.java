package com.manavault.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
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
}
