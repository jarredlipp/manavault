package com.manavault.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Complete printing-level SQLite artwork index. Never silently selects a printing.
 * The UI must cross-check scores against independent collector/set evidence.
 * Each read is indexed by Scryfall printing ID, not a linear 120k-card scan.
 */
final class FullArtworkIndex {
    private static final String TAG="ManaVaultFullArt";
    static final String FILE="full-artwork-index-v1.sqlite";
    private static SQLiteDatabase db;
    private static int count;
    private static String version="";
    private static final String[] PARTS={"art","wide","title","type","text","footer"};
    private static final float[] WEIGHTS={.29f,.14f,.09f,.07f,.18f,.10f};

    static synchronized boolean ready(Context context) {
        if(db!=null && db.isOpen())return true;
        File file=new File(context.getFilesDir(),FILE);
        if(!file.isFile())return false;
        try {
            SQLiteDatabase opened=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,
                SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            if(!"1".equals(info(opened,"schema")))throw new IllegalStateException("Unsupported index schema");
            try(Cursor c=opened.rawQuery("SELECT COUNT(*) FROM artwork",null)){
                if(!c.moveToFirst() || c.getInt(0)<1)throw new IllegalStateException("Empty artwork index");
                count=c.getInt(0);
            }
            version=info(opened,"version");db=opened;
            return true;
        }catch(Exception e){Log.w(TAG,"Full artwork database not available",e);return false;}
    }
    static synchronized void close(){if(db!=null){db.close();db=null;}count=0;version="";}
    static synchronized int totalFaces(Context context){return ready(context)?count:0;}
    static synchronized String status(Context context){
        return ready(context)?"Complete printing-level artwork index: "+count+" faces · "+version:
            "Full offline artwork index not installed; bundled fallback active";
    }
    static synchronized String installedVersion(Context context){return ready(context)?version:"";}
    private static String info(SQLiteDatabase database,String key){
        try(Cursor c=database.rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }

    static byte[] descriptor(Bitmap image,float x0,float y0,float x1,float y1){
        int w=image.getWidth(),h=image.getHeight();
        int x=Math.max(0,Math.min(w-1,Math.round(w*x0)));
        int y=Math.max(0,Math.min(h-1,Math.round(h*y0)));
        int width=Math.min(w-x,Math.max(1,Math.round(w*(x1-x0))));
        int height=Math.min(h-y,Math.max(1,Math.round(h*(y1-y0))));
        Bitmap crop=Bitmap.createBitmap(image,x,y,width,height);
        Bitmap small=Bitmap.createScaledBitmap(crop,8,8,true);
        double[] values=new double[64];double mean=0;
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            int rgb=small.getPixel(col,row);
            double v=.299*Color.red(rgb)+.587*Color.green(rgb)+.114*Color.blue(rgb);
            values[row*8+col]=v;mean+=v;
        }
        mean/=64.0;double variance=0;
        for(double v:values)variance+=(v-mean)*(v-mean);
        double sigma=Math.max(16,Math.sqrt(variance/64.0));
        byte[] result=new byte[64];
        for(int i=0;i<64;i++)result[i]=(byte)Math.min(255,Math.max(0,Math.round(128+(values[i]-mean)*48/sigma)));
        small.recycle();crop.recycle();return result;
    }
    /** Only artwork pixels may shift for alignment rescue. Text/footer stay
     * in the physically photographed positions to preserve printing evidence. */
    static byte[][] alignedArtDescriptors(Bitmap image){
        return new byte[][]{
            descriptor(image,.077f,.15f,.897f,.49f),
            descriptor(image,.103f,.15f,.923f,.49f),
            descriptor(image,.09f,.138f,.91f,.478f),
            descriptor(image,.09f,.162f,.91f,.502f)
        };
    }
    static byte[] colorDescriptor(Bitmap image){
        int w=image.getWidth(),h=image.getHeight();
        Bitmap crop=Bitmap.createBitmap(image,Math.round(w*.09f),Math.round(h*.15f),
            Math.round(w*.82f),Math.round(h*.34f));
        Bitmap small=Bitmap.createScaledBitmap(crop,8,8,true);
        int[][] channels=new int[3][64];double[] mean=new double[3],std=new double[3];
        for(int i=0;i<64;i++){
            int value=small.getPixel(i%8,i/8);
            channels[0][i]=Color.red(value);channels[1][i]=Color.green(value);channels[2][i]=Color.blue(value);
            for(int j=0;j<3;j++)mean[j]+=channels[j][i];
        }
        for(int j=0;j<3;j++){
            mean[j]/=64;
            for(int i=0;i<64;i++)std[j]+=(channels[j][i]-mean[j])*(channels[j][i]-mean[j]);
            std[j]=Math.max(20,Math.sqrt(std[j]/64));
        }
        byte[] out=new byte[192];
        for(int i=0;i<64;i++)for(int j=0;j<3;j++)
            out[i*3+j]=(byte)Math.min(255,Math.max(0,Math.round(128+(channels[j][i]-mean[j])*48/std[j])));
        small.recycle();crop.recycle();return out;
    }
    // The reference builder and Android MUST use identical normalized card regions.
    static byte[][] describe(Bitmap image){
        return new byte[][]{
            descriptor(image,.09f,.15f,.91f,.49f),
            descriptor(image,.07f,.12f,.93f,.77f),
            descriptor(image,.07f,.025f,.93f,.155f),
            descriptor(image,.07f,.60f,.93f,.73f),
            descriptor(image,.075f,.72f,.925f,.90f),
            descriptor(image,.04f,.905f,.96f,.985f)
        };
    }
    // Indexed global candidate generator for difficult full-art/alternate-title
    // layouts. Eight 8-bit perceptual-hash bands avoid scanning 120k rows;
    // results are suggestions only, NEVER authority for a printing or card name.
    static String artHash(Bitmap image){return artHash(image,0f,0f);}
    /** Shift the art probe only; never move the photographed collector region.
     * The underlying card has already been rectified by CardQuadDetector.
     * Small camera/framing offsets must not erase the correct candidate. */
    static String artHash(Bitmap image,float dx,float dy){
        int w=image.getWidth(),h=image.getHeight();
        int x=Math.max(0,Math.min(w-1,Math.round(w*(.09f+dx))));
        int y=Math.max(0,Math.min(h-1,Math.round(h*(.15f+dy))));
        int cw=Math.max(1,Math.min(w-x,Math.round(w*.82f)));
        int ch=Math.max(1,Math.min(h-y,Math.round(h*.34f)));
        Bitmap crop=Bitmap.createBitmap(image,x,y,cw,ch);
        Bitmap thumb=Bitmap.createScaledBitmap(crop,9,8,true);
        long hash=0;
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            int a=thumb.getPixel(col,row),b=thumb.getPixel(col+1,row);
            double va=.299*Color.red(a)+.587*Color.green(a)+.114*Color.blue(a);
            double vb=.299*Color.red(b)+.587*Color.green(b)+.114*Color.blue(b);
            if(va>vb)hash|=1L<<(row*8+col);
        }
        thumb.recycle();crop.recycle();
        return String.format(java.util.Locale.ROOT,"%016x",hash);
    }
    static synchronized JSONObject nearest(Context context,File scan,int limit)throws Exception {
        Bitmap bitmap=BitmapFactory.decodeFile(scan.getAbsolutePath());
        if(bitmap==null)return new JSONObject().put("ready",false);
        try{return nearest(context,bitmap,limit);}finally{bitmap.recycle();}
    }
    static synchronized JSONObject nearest(Context context,Bitmap bitmap,int limit)throws Exception {
        JSONObject result=new JSONObject();JSONArray choices=new JSONArray();result.put("choices",choices);
        if(!ready(context)){result.put("ready",false);return result;}
        byte[][] signature=describe(bitmap);
        byte[] photographedColor=colorDescriptor(bitmap);
        long central=Long.parseUnsignedLong(artHash(bitmap),16);
        ArrayList<JSONObject> ranked=searchCandidates(
            ArtworkProbePolicy.probes(new long[]{central},false),signature,photographedColor,2,240);
        // No match or a poor one: relax only the candidate GENERATOR, never
        // any final collector/set verification thresholds. On a clean scan
        // these extra SQLite queries and image crops are not performed.
        boolean weak=ranked.isEmpty() || ranked.get(0).optDouble("score",0)<.945;
        if(weak){
            long[] shifted={central,
                Long.parseUnsignedLong(artHash(bitmap,-.013f,0),16),
                Long.parseUnsignedLong(artHash(bitmap,.013f,0),16),
                Long.parseUnsignedLong(artHash(bitmap,0,-.012f),16),
                Long.parseUnsignedLong(artHash(bitmap,0,.012f),16)};
            ranked=searchCandidates(ArtworkProbePolicy.probes(shifted,true),
                signature,photographedColor,1,360);
        }
        ranked.sort((a,b)->Double.compare(b.optDouble("score"),a.optDouble("score")));
        HashSet<String> names=new HashSet<>();
        for(JSONObject candidate:ranked){
            if(names.add(candidate.optString("nameKey","")))choices.put(candidate);
            if(choices.length()>=Math.max(1,Math.min(10,limit)))break;
        }
        result.put("ready",true).put("indexed",true).put("expanded",weak);
        result.put("examined",ranked.size());
        return result;
    }
    /** Bounded indexed search: first strict votes, then rare one-bit-neighbor
     * and cropped-position probes. Does not traverse every stored image.
     * The SQL uses the art_band_search index; a row is scored only after
     * it survives candidate generation. */
    private static ArrayList<JSONObject> searchCandidates(List<int[]> probes,
            byte[][] signature,byte[] photographedColor,int minVotes,int bound) throws Exception{
        ArrayList<JSONObject> found=new ArrayList<>();
        if(probes.isEmpty())return found;
        StringBuilder slots=new StringBuilder();ArrayList<String> args=new ArrayList<>();
        for(int[] probe:probes){
            if(slots.length()>0)slots.append(',');
            slots.append("(?,?)");
            args.add(String.valueOf(probe[0]));args.add(String.valueOf(probe[1]));
        }
        args.add(String.valueOf(minVotes));args.add(String.valueOf(bound));
        String sql="WITH probes(band,code) AS (VALUES "+slots+"), "+
            "hits AS (SELECT b.printingId,b.face,COUNT(*) AS votes FROM probes p "+
            "JOIN art_band b INDEXED BY art_band_search "+
            "ON b.band=p.band AND b.code=p.code GROUP BY b.printingId,b.face "+
            "HAVING COUNT(*)>=? ORDER BY votes DESC LIMIT ?) "+
            "SELECT a.printingId,a.face,a.nameKey,a.setCode,a.collectorKey,"+
            "a.illustrationId,a.art,a.artcolor,a.wide,a.title,a.type,a.text,a.footer,"+
            "hits.votes FROM hits JOIN artwork a ON "+
            "a.printingId=hits.printingId AND a.face=hits.face ORDER BY hits.votes DESC";
        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){
                float score=0;boolean valid=true;
                for(int part=0;part<6;part++){
                    float sim=similarity(signature[part],c.getBlob(part==0?6:part+7));
                    if(sim<0){valid=false;break;}
                    score+=WEIGHTS[part]*sim;
                }
                if(!valid)continue;
                float color=similarity(photographedColor,c.getBlob(7));
                if(color<0)continue;
                score+=.13f*color;
                found.add(new JSONObject().put("id",c.getString(0))
                    .put("face",c.getInt(1)).put("nameKey",c.getString(2))
                    .put("set",c.getString(3)).put("collector",c.getString(4))
                    .put("illustration_id",c.getString(5))
                    .put("score",Math.round(score*10000)/10000.0)
                    .put("bands",c.getInt(13)));
            }
        }catch(Exception e){Log.w(TAG,"Indexed artwork search failed",e);}
        return found;
    }
    static float similarity(byte[] captured,byte[] reference){
        if(captured==null||reference==null||captured.length!=reference.length||
           (captured.length!=64 && captured.length!=192))return -1;
        long distance=0;
        for(int i=0;i<captured.length;i++)distance+=Math.abs((captured[i]&255)-(reference[i]&255));
        return Math.max(0,1f-distance/(captured.length*255f));
    }
    /** Bounded, fast SELECT by primary key for the title's plausible printings.
     * Matching illustrations are not treated as distinct independent evidence.
     * The web resolver must additionally require printed collector/symbol marks
     * when several printings share the illustration or layout.
     */
    static synchronized JSONObject rank(Context context,File scan,JSONObject request) throws Exception {
        JSONObject out=new JSONObject().put("requestId",request.optString("requestId",""));
        if(!ready(context)){out.put("ready",false);out.put("error","Full index not installed");return out;}
        JSONArray choices=request.optJSONArray("choices");
        if(choices==null||choices.length()<1||choices.length()>256){out.put("error","Printing candidate count must be 1–256");return out;}
        Bitmap image=BitmapFactory.decodeFile(scan.getAbsolutePath());
        if(image==null){out.put("error","Card photograph unavailable");return out;}
        byte[][] observed,artOffsets;byte[] photographedColor;
        try{observed=describe(image);photographedColor=colorDescriptor(image);
            artOffsets=alignedArtDescriptors(image);}finally{image.recycle();}
        List<JSONObject> scored=new ArrayList<>();int missing=0;
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<choices.length();i++){
            JSONObject choice=choices.optJSONObject(i);if(choice==null){missing++;continue;}
            String id=choice.optString("id","");
            if(id.isEmpty()||!ids.add(id)){missing++;continue;}
            float top=-1;JSONObject winner=null;
            try(Cursor c=db.rawQuery("SELECT illustrationId,treatment,art,artcolor,wide,title,type,text,footer,face " +
                    "FROM artwork WHERE printingId=?",new String[]{id})){
                while(c.moveToNext()){
                    float score=0;JSONArray partScores=new JSONArray();boolean valid=true;
                    float[] partValues=new float[6];
                    for(int j=0;j<6;j++){
                        float s=similarity(observed[j],c.getBlob(j==0?2:j+3));
                        if(s<0){valid=false;break;}
                        // Small artwork shifts rescue framing on showcase / borderless
                        // cards; never shift title, text, footer or frame evidence.
                        if(j==0 && s<.955f){
                            byte[] target=c.getBlob(2);
                            for(byte[] shifted:artOffsets)
                                s=Math.max(s,similarity(shifted,target)-.012f);
                        }
                        partScores.put(Math.round(s*10000)/10000.0);
                        partValues[j]=s;score+=WEIGHTS[j]*s;
                    }
                    if(!valid)continue;
                    float color=similarity(photographedColor,c.getBlob(3));
                    if(color<0)continue;
                    score+=.13f*color;
                    if(score>top){top=score;winner=new JSONObject()
                        .put("id",id).put("score",Math.round(score*10000)/10000.0)
                        .put("illustration_id",c.getString(0)).put("treatment",c.getString(1))
                        .put("parts",partScores).put("artScore",partValues[0])
                        .put("textScore",partValues[4]).put("footerScore",partValues[5])
                        .put("face",c.getInt(9));}
                }
            }
            if(winner==null)missing++;else scored.add(winner);
        }
        scored.sort((a,b)->Double.compare(b.optDouble("score"),a.optDouble("score")));
        JSONArray matches=new JSONArray();for(JSONObject x:scored)matches.put(x);
        out.put("ready",true).put("fullIndex",true).put("attempted",choices.length())
            .put("failed",missing).put("matches",matches).put("indexedFaces",count);
        if(missing>0)out.put("error","Full artwork index lacks "+missing+" candidate reference(s); manual verification required");
        return out;
    }
}
