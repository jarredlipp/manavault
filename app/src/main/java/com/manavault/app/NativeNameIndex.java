package com.manavault.app;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fast offline, canonical-name lookup supplied by the public Scryfall name catalog.
 * An OCR title is never considered a Scryfall printing ID. */
final class NativeNameIndex {
    private final Map<String,String> names=new HashMap<>();
    static String norm(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();
    }
    NativeNameIndex(Context context){
        File file=new File(context.getFilesDir(),"native-card-names.json");
        if(!file.isFile()||file.length()>3_000_000)return;
        try(FileInputStream in=new FileInputStream(file)){
            byte[] data=new byte[(int)file.length()];
            int offset=0,n;
            while(offset<data.length&&(n=in.read(data,offset,data.length-offset))>0)offset+=n;
            JSONArray list=new JSONArray(new String(data,0,offset,StandardCharsets.UTF_8));
            for(int i=0;i<list.length();i++){
                String full=list.optString(i,"");
                if(full.isEmpty())continue;
                put(full,full,true);
                for(String face:full.split(" // "))put(face,full,false);
            }
        }catch(Exception e){Log.w("ManaVaultNames","Native name index unavailable; OCR repetition fallback",e);names.clear();}
    }
    private void put(String alias,String canonical,boolean whole){String key=norm(alias);if(!key.isEmpty()&&(whole||!names.containsKey(key)))names.put(key,canonical);}
    int size(){return names.size();}
    String exact(String text){
        final String key=norm(text);
        String known=names.get(key);
        if(known!=null)return known;
        // Scryfall's generic catalog/card-names list can omit named tokens.
        // Check the bundled full printing database before treating a clearly
        // read short token title (e.g. Jace) as unknown. This is a name lookup,
        // never an assertion of a particular printing or set.
        if(key.length()<3 || key.length()>55 || !NativePrintingIndex.status().startsWith("Ready:"))return null;
        try{
            JSONObject result=new JSONObject(NativePrintingIndex.lookup("name",key,64));
            JSONArray rows=result.optJSONArray("rows");
            if(rows==null)return null;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row!=null && !row.optBoolean("digital",false) && key.equals(norm(row.optString("name","")))){
                    String canonical=row.optString("name","");
                    names.put(key,canonical);
                    return canonical;
                }
            }
        }catch(Exception e){Log.w("ManaVaultNames","Native printing-name fallback failed",e);}
        return null;
    }
}
