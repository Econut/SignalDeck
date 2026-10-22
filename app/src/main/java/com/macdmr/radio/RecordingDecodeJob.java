/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.graphics.Bitmap;
import android.os.Environment;

import com.macdmr.radio.pkt.APRSPacketDecoder;
import com.macdmr.radio.pkt.Ax25;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Decode a recording on the phone (SSTV page, APRS page, SignalDeck Web): one job at a time, in the background, with
 * progress that the screens poll. SSTV pictures go to Download/DMR/SSTV like live ones, named after the time they were
 * on the air ("…_rec.png"); APRS packets are listed and written next to the recording as "<name>.aprs.txt".
 */
public final class RecordingDecodeJob {
    public static final String SSTV = "sstv", APRS = "aprs";

    public final String mode; public final File file;
    public volatile int percent; public volatile boolean done; public volatile String error;
    public final List<String> pictures = Collections.synchronizedList(new ArrayList<>());   // saved file names
    public final List<String> packets = Collections.synchronizedList(new ArrayList<>());    // "12:05  CALL>DEST: description"
    public volatile String summary;
    public final long startedAt = System.currentTimeMillis();
    private final RecordingDecoder dec = new RecordingDecoder();

    private static volatile RecordingDecodeJob current;
    public static RecordingDecodeJob current() { return current; }

    private RecordingDecodeJob(String mode, File file) { this.mode = mode; this.file = file; }

    /** The recordings SignalDeck made, newest first (pass recordings and REC), for the pickers. */
    public static List<File> recordings(int max) {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DMR/Audio");
        List<File> out = new ArrayList<>();
        File[] dirs = root.listFiles(); if (dirs == null) return out;
        for (File d : dirs) { File[] fs = d.isDirectory() ? d.listFiles() : null; if (fs != null) for (File f : fs) if (f.getName().toLowerCase(Locale.US).endsWith(".wav") && f.length() > 44) out.add(f); }
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
    /** A recording by its folder and name (SignalDeck Web), or null if it is not one of ours. */
    public static File recording(String folder, String name) {
        if (folder == null || name == null || folder.contains("..") || name.contains("..") || folder.contains("/") || name.contains("/")) return null;
        File f = new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DMR/Audio/" + folder), name);
        return f.isFile() && name.toLowerCase(Locale.US).endsWith(".wav") ? f : null;
    }

    /** Start decoding (null), or why not. {@code onDone} runs on the job's thread. */
    public static synchronized String start(String mode, File f, RadioService svc) {
        RecordingDecodeJob j = current;
        if (j != null && !j.done) return "A recording is already being decoded (" + j.file.getName() + ")";
        if (f == null || !f.isFile()) return "Recording not found";
        if (!SSTV.equals(mode) && !APRS.equals(mode)) return "Unknown mode";
        final RecordingDecodeJob job = new RecordingDecodeJob(mode, f); current = job;
        Thread t = new Thread(() -> job.run(svc), "decode-recording"); t.setPriority(Thread.MIN_PRIORITY); t.start();
        return null;
    }
    public void cancel() { dec.cancel(); }

    private void run(RadioService svc) {
        try {
            short[] s = RecordingDecoder.read8k(Files.readAllBytes(file.toPath()));
            final long start = RecordingDecoder.startFromName(file.getName()) > 0 ? RecordingDecoder.startFromName(file.getName()) : file.lastModified() - s.length * 1000L / RecordingDecoder.SR;
            RecordingDecoder.Listener l = new RecordingDecoder.Listener() {
                @Override public void progress(int p) { percent = p; }
                @Override public void picture(int[] argb, int w, int h, int lines, boolean complete, long atMs) {
                    String n = savePicture(argb, w, h, complete, start + atMs); if (n != null) pictures.add(n);
                }
                @Override public void packet(byte[] frame, long atMs) { packets.add(describe(frame, atMs, start)); }
            };
            int n = SSTV.equals(mode) ? dec.sstv(s, l) : dec.aprs(s, l);
            if (dec.cancelled()) { summary = "Stopped"; return; }
            percent = 100;
            String len = RecordingDecoder.clock(s.length * 1000L / RecordingDecoder.SR);
            if (APRS.equals(mode)) {
                if (n > 0) try { Files.write(new File(file.getParentFile(), file.getName() + ".aprs.txt").toPath(), (String.join("\n", packets) + "\n").getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
                summary = n == 0 ? "No packets in " + len + " of audio" : n + " packet" + (n == 1 ? "" : "s") + " in " + len + " of audio";
            } else {
                long whole = 0; for (String p : pictures) if (!p.contains("_partial")) whole++;
                summary = n == 0 ? "No Robot 36 picture in " + len + " of audio" : n + " picture" + (n == 1 ? "" : "s") + " (" + whole + " complete) in " + len + " of audio, saved to Download/DMR/SSTV";
            }
            if (svc != null) svc.history("pkt", "📼 Decoded " + file.getName() + ": " + summary);
        } catch (Throwable t) { error = t.getMessage() == null ? t.toString() : t.getMessage(); }
        finally { done = true; }
    }

    private static String savePicture(int[] argb, int w, int h, boolean complete, long when) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DMR/SSTV"); dir.mkdirs();
            String base = "SSTV_Robot36_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(when)) + "_rec" + (complete ? "" : "_partial");
            File f = new File(dir, base + ".png"); for (int k = 2; f.exists(); k++) f = new File(dir, base + "_" + k + ".png");   // decoding twice keeps both
            Bitmap b = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
            try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
            f.setLastModified(when);                                                // sorts with the pass, not with today
            return f.getName();
        } catch (Throwable t) { return null; }
    }

    static String describe(byte[] frame, long atMs, long start) {
        String clock = RecordingDecoder.clock(atMs) + "  " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(start + atMs));
        try {
            APRSPacketDecoder.APRSPacket p = APRSPacketDecoder.decode(frame);
            String d = p.isValid ? APRSPacketDecoder.describe(p) : null;
            return clock + "  " + Ax25.tnc2(frame) + (d == null || d.trim().isEmpty() ? "" : "  — " + d.trim());
        } catch (Throwable t) { return clock + "  " + Arrays.toString(frame); }
    }
}
