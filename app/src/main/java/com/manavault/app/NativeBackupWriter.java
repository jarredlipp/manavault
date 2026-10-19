package com.manavault.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Portable, independently testable writer for collection backups.
 * The caller owns opening the Android SAF streams. Nothing modifies localStorage. */
final class NativeBackupWriter {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    interface OutputOpener { OutputStream open() throws IOException; }
    interface InputOpener { InputStream open() throws IOException; }

    static void validateSize(String json) {
        if (json == null || json.isEmpty()) throw new IllegalArgumentException("Collection is empty or unavailable");
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Backup exceeds 32 MB export limit");
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Backup exceeds 32 MB export limit");
    }

    static String safeFilename(String name) {
        String file = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (file.isEmpty()) file = "manavault-backup.json";
        if (file.length() > 100) file = file.substring(0, 95);
        if (!file.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) file += ".json";
        return file;
    }

    static long writeAndVerify(byte[] data, OutputOpener outputOpener, InputOpener inputOpener) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_BYTES)
            throw new IOException("Invalid backup size");
        try (OutputStream out = outputOpener.open()) {
            if (out == null) throw new IOException("Chosen location does not allow writing");
            out.write(data);
            out.flush();
        }
        byte[] expected = sha256(data);
        MessageDigest readHash = digest();
        long bytes = 0;
        try (InputStream in = inputOpener.open()) {
            if (in == null) throw new IOException("Saved file cannot be read back for verification");
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bytes += n;
                if (bytes > MAX_BYTES) throw new IOException("Saved file larger than expected");
                readHash.update(buffer, 0, n);
            }
        }
        if (bytes != data.length || !Arrays.equals(expected, readHash.digest()))
            throw new IOException("Saved file does not match the original collection data");
        return bytes;
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 missing", e); }
    }
    private static byte[] sha256(byte[] content) { return digest().digest(content); }
    private NativeBackupWriter() {}
}
