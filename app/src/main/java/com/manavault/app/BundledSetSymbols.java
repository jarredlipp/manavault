package com.manavault.app;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact signatures derived from Scryfall's actual printed expansion-symbol area.
 * No runtime download or image decoding. An absent set remains unresolved.
 */
final class BundledSetSymbols {
    private static final Map<String,List<double[]>> SETS=new HashMap<>();
    private static volatile boolean ready;
    private BundledSetSymbols(){}
    static synchronized void initialize(Context context){
        if(ready)return;
        try(InputStream stream=context.getAssets().open("symbol-index.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=stream.read(buffer))!=-1){if(out.size()+n>4_000_000)throw new IllegalArgumentException("Set index too large");out.write(buffer,0,n);}
            JSONObject root=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            if(!"manavault-symbol-v1".equals(root.optString("format")))throw new IllegalArgumentException("Invalid set index version");
            JSONArray sets=root.optJSONArray("sets");
            if(sets!=null)for(int i=0;i<sets.length();i++){
                JSONObject row=sets.optJSONObject(i);if(row==null)continue;
                String code=row.optString("set","").toLowerCase(Locale.ROOT);
                if(!code.matches("[a-z0-9]{2,5}"))continue;
                JSONArray samples=row.optJSONArray("f");List<double[]> fingerprints=new ArrayList<>();
                if(samples!=null)for(int j=0;j<samples.length();j++){
                    byte[] raw=Base64.decode(samples.optString(j,""),Base64.DEFAULT);
                    if(raw.length!=1728)continue;
                    double[] result=new double[1728];for(int k=0;k<1728;k++)result[k]=raw[k]/127.0;
                    fingerprints.add(result);
                }
                if(!fingerprints.isEmpty())SETS.put(code,fingerprints);
            }
            Log.i("ManaVaultSymbols","Bundled symbol sets: "+SETS.size());
        }catch(Exception e){Log.w("ManaVaultSymbols","No bundled symbol atlas, review fallback",e);}
        ready=true;
    }
    static List<double[]> forSet(String set){
        return SETS.get(set==null?"":set.toLowerCase(Locale.ROOT));
    }
    static int count(){return SETS.size();}
}
