package com.manavault.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NativeBackupWriterTest {
    private static void eq(Object got, Object expected) {
        if (!got.equals(expected)) throw new AssertionError("Expected " + expected + ", got " + got);
    }
    private static void failIfNotRejected(Runnable test) {
        try { test.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) throws Exception {
        String snapshot = "{\"cards\":[{\"id\":\"abc\",\"qty\":3,\"name\":\"Æther Adept\"}],\"decks\":[]}";
        NativeBackupWriter.validateSize(snapshot);
        eq(NativeBackupWriter.safeFilename("../../bad name"), ".._.._bad_name.json");
        eq(NativeBackupWriter.safeFilename("ManaVault-export.json"), "ManaVault-export.json");
        failIfNotRejected(() -> NativeBackupWriter.validateSize(null));
        byte[] content = snapshot.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        long saved = NativeBackupWriter.writeAndVerify(content, () -> stored, () -> new ByteArrayInputStream(stored.toByteArray()));
        eq(saved, (long) content.length);
        eq(Arrays.equals(content, stored.toByteArray()), true);
        try {
            NativeBackupWriter.writeAndVerify(content, ByteArrayOutputStream::new,
                () -> new ByteArrayInputStream("truncated".getBytes(StandardCharsets.UTF_8)));
            throw new AssertionError("Corrupt file incorrectly verified");
        } catch (IOException expected) { }
        try {
            NativeBackupWriter.writeAndVerify(content, () -> {throw new IOException("disk full");},
                () -> new ByteArrayInputStream(content));
            throw new AssertionError("Failed save incorrectly verified");
        } catch (IOException expected) { }
        try {
            NativeBackupWriter.writeAndVerify(content, ByteArrayOutputStream::new,
                () -> {throw new IOException("provider unreadable");});
            throw new AssertionError("Unverifiable file was accepted");
        } catch (IOException expected) { }
        System.out.println("Native backup writer: save, UTF-8 round-trip, mismatch and write error passed");
    }
}
