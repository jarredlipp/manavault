package com.manavault.app;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
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
    String exact(String text){return names.get(norm(text));}
}
