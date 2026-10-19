package com.manavault.app;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Full physical-set icon library built from Scryfall's icon_svg_uri SVGs.
 * Small, normalized grayscale shape masks rather than a few card-image crops.
 * Missing or indistinguishable icons are NEVER proof of a particular printing.
 */
final class BundledSetSymbols {
    private static final Map<String,byte[]> SETS = new HashMap<>();
    private static volatile boolean ready;
    private static int eligible;
    private BundledSetSymbols() {}
    static synchronized void initialize(Context context) {
        if (ready) return;
        try (InputStream stream=context.getAssets().open("symbol-index.json")) {
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192]; int read;
            while((read=stream.read(buffer))!=-1) {
                if(output.size()+read>5_000_000)throw new IllegalArgumentException("Symbol catalog too large");
                output.write(buffer,0,read);
            }
            JSONObject root=new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            if(!"manavault-symbol-v3".equals(root.optString("format")))
                throw new IllegalArgumentException("Unsupported set-icon atlas version");
            eligible=root.optJSONObject("coverage") == null ? 0 : root.optJSONObject("coverage").optInt("icon_eligible",0);
            JSONArray rows=root.optJSONArray("sets");
            if(rows!=null)for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if(row==null)continue;
                String code=row.optString("set","").toLowerCase(Locale.ROOT);
                if(!code.matches("[a-z0-9]{2,8}"))continue;
                byte[] raw=Base64.decode(row.optString("shape",""),Base64.DEFAULT);
                if(raw.length==576)SETS.put(code,raw);
            }
            Log.i("ManaVaultSymbols","Offline dedicated set icons: "+SETS.size()+" of "+eligible+" available physical sets");
        }catch(Exception error){Log.w("ManaVaultSymbols","Dedicated set-icon atlas unavailable; printing review remains enabled",error);}
        ready=true;
    }
    static byte[] icon(String setCode) {
        return SETS.get(setCode==null?"":setCode.toLowerCase(Locale.ROOT));
    }
    static int count(){return SETS.size();}
    static int eligibleCount(){return eligible;}
}
