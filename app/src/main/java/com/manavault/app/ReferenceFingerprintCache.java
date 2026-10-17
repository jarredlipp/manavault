package com.manavault.app;

import android.content.Context;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** Persistent, bounded cache of *reference signatures*, not copyrighted images.
 * All cache keys originate from validated Scryfall HTTPS image URLs. Cached
 * signatures are clues for comparison, never proofs of an exact printing.
 */
final class ReferenceFingerprintCache {
    private static final String TAG = "ManaVaultRefCache";
    private static final int MAGIC = 0x4d565246; // MVRF
    private static final int LENGTH = 576;
    private static final int MAX_FILES = 2500;
    private static volatile File directory;
    private static int newWrites;
    private ReferenceFingerprintCache() {}

    static synchronized void configure(Context context) {
        if(directory != null) return;
        File dir = new File(context.getFilesDir(), "scryfall-printing-fingerprints-v1");
        if(!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Fingerprint cache directory unavailable");
            return;
        }
        directory = dir;
        prune(dir);
    }

    static int count() {
        File dir = directory;
        if(dir == null) return 0;
        String[] names = dir.list((parent, filename) -> filename.endsWith(".bin"));
        return names == null ? 0 : names.length;
    }

    private static File file(String kind, String url) throws Exception {
        File dir = directory;
        if(dir == null || !("art".equals(kind) || "symbol".equals(kind))) return null;
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] digest = hash.digest((kind + "\n" + url).getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder(digest.length * 2);
        for(byte b : digest) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return new File(dir, name + ".bin");
    }

    static double[] get(String kind, String url) {
        try {
            File f = file(kind,url);
            if(f == null || !f.isFile() || f.length() != 4 + 4 + LENGTH*8) return null;
            double[] values = new double[LENGTH];
            try(DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                if(in.readInt() != MAGIC || in.readInt() != LENGTH) return null;
                for(int i=0;i<LENGTH;i++) {
                    values[i]=in.readDouble();
                    if(!Double.isFinite(values[i]) || Math.abs(values[i]) > 1.001) return null;
                }
            }
            // LRU-ish eviction: reference comparison refreshes most recently used.
            f.setLastModified(System.currentTimeMillis());
            return values;
        } catch(Exception e) { Log.w(TAG,"Fingerprint cache read failed",e); return null; }
    }

    static synchronized void put(String kind, String url, double[] values) {
        if(values==null || values.length!=LENGTH) return;
        for(double v : values) if(!Double.isFinite(v) || Math.abs(v)>1.001) return;
        File f;
        try { f=file(kind,url); }catch(Exception e){Log.w(TAG,"Cache key failed",e);return;}
        if(f == null) return;
        File tmp = new File(f.getParentFile(), f.getName()+".tmp");
        try(DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
            out.writeInt(MAGIC);out.writeInt(LENGTH);
            for(double v : values) out.writeDouble(v);
        }catch(Exception e){tmp.delete();Log.w(TAG,"Cache write failed",e);return;}
        if(!tmp.renameTo(f)){
            // Atomic rename can fail on some OEM filesystems if target exists.
            if(!f.delete() || !tmp.renameTo(f)) {tmp.delete();Log.w(TAG,"Fingerprint not persisted");return;}
        }
        if(++newWrites % 40 == 0) prune(directory);
    }

    private static void prune(File dir) {
        File[] all = dir.listFiles((parent,filename)->filename.endsWith(".bin"));
        if(all==null || all.length<=MAX_FILES)return;
        Arrays.sort(all,Comparator.comparingLong(File::lastModified));
        for(int i=0;i<all.length-MAX_FILES;i++)all[i].delete();
    }
}
