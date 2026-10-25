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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fast offline, canonical-name lookup supplied by the public Scryfall name catalog.
 * An OCR title is never considered a Scryfall printing ID. */
final class NativeNameIndex {
    private final Map<String,String> names=new HashMap<>();
    private final Map<String,String> distinctiveFirstWords=new HashMap<>();
    private final Map<String,List<String>> correctionBuckets=new HashMap<>();
    static String norm(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKD)
            .replace("Æ","Ae").replace("æ","ae").replace("Œ","Oe").replace("œ","oe")
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
                String bucket=TitleRecoveryPolicy.keyPrefix(norm(full));
                if(!bucket.isEmpty())correctionBuckets.computeIfAbsent(bucket,k->new ArrayList<>()).add(full);
                for(String face:full.split(" // ")){
                    if(FaceAliasPolicy.safeFaceAlias(face))put(face,full,false);
                    String[] words=norm(face).split(" ");
                    if(words.length>=2 && words[0].length()>=7){
                        String prior=distinctiveFirstWords.get(words[0]);
                        if(!distinctiveFirstWords.containsKey(words[0]))distinctiveFirstWords.put(words[0],full);
                        else if(prior!=null && !prior.equals(full))distinctiveFirstWords.put(words[0],null);
                    }
                }
            }
        }catch(Exception e){Log.w("ManaVaultNames","Native name index unavailable; OCR repetition fallback",e);names.clear();distinctiveFirstWords.clear();correctionBuckets.clear();}
    }
    private void put(String alias,String canonical,boolean whole){String key=norm(alias);if(!key.isEmpty()&&(whole||!names.containsKey(key)))names.put(key,canonical);}
    int size(){return names.size();}
    /** Only a *suggested* identity. Requires two observations and independent
     * printing evidence before a partially read stylized title can be saved. */
    String distinctiveTitlePrefix(String raw){
        if(TitleReadingPolicy.isFooterOrRulesNoise(raw))return null;
        String key=norm(raw);
        if(!key.matches("[a-z]{7,24}"))return null;
        if(distinctiveFirstWords.containsKey(key))return distinctiveFirstWords.get(key);
        // Stylized uppercase card titles commonly confuse a single glyph
        // (WEPSTRIKE vs WEBSTRIKE). Only a UNIQUE near-first-word match may
        // be returned, and it remains an OCR CANDIDATE requiring another read
        // and independent physical printing evidence before saving.
        return TitlePrefixMatcher.uniqueOneSubstitution(key,distinctiveFirstWords);
    }
    /** Conservative OCR repair, always an unverified name suggestion. */
    String suggestTitle(String observed){
        String key=norm(observed);
        if(key.length()<9 || TitleReadingPolicy.isFooterOrRulesNoise(observed))return null;
        // Try literal catalog title with a stray isolated OCR digit removed.
        String stripped=TitleRecoveryPolicy.withoutTrailingArtifact(observed);
        if(!stripped.equals(key)){
            String exact=names.get(stripped);
            if(exact!=null && !exact.contains(" // "))return exact;
        }
        List<String> bucket=correctionBuckets.get(TitleRecoveryPolicy.keyPrefix(key));
        return bucket==null?null:TitleRecoveryPolicy.uniqueCandidate(observed,bucket);
    }
    String exact(String text){
        final String key=norm(text);
        if(TitleReadingPolicy.isFooterOrRulesNoise(text))return null;
        String known=names.get(key);
        if(known!=null)return known;
        // Common ML Kit glyph confusions in the PRINTED TITLE only. The
        // replacement must be an exact indexed name; never fuzzy-accept it.
        if(key.startsWith("ether ")){
            known=names.get("a"+key);
            if(known!=null)return known;
        }
        if(key.startsWith("oinvasion ") && key.length()>12){
            known=names.get(key.substring(1));
            if(known!=null)return known;
        }
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
