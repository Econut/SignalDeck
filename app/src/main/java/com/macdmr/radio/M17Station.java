/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import com.macdmr.radio.ham.Codec2;
import com.macdmr.radio.ham.M17;
import com.macdmr.radio.ham.M17Receiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M17 receive on the current FM channel: the equalizing receiver, Codec 2 voice to the phone's speaker (the radio's own
 * audio, which would be 4FSK noise, is held back while a transmission is decoded), and a list of what was heard.
 * Receive only: this radio's transmit path cannot produce M17 that M17 radios decode.
 */
public final class M17Station {
    public interface Host { void event(String line); void heard(String call); void changed(); default void voice(short[] pcm) { } }

    /** One transmission heard. */
    public static final class Heard {
        public final long start; public long end; public String src = "", dst = "", meta; public int frames, good; public boolean encrypted, data;
        Heard(long t) { start = t; end = t; }
    }

    private final Context ctx; private final Host host; private final String profileId;
    private M17Receiver rx; private Codec2 codec;
    private AudioTrack track;
    private volatile boolean running;
    private volatile Heard current;
    private final List<Heard> heard = new ArrayList<>();
    private volatile float quality;
    /** Bench: also append the decoded voice to cache/m17_out.raw (8 kHz 16-bit mono). */
    public static volatile boolean benchDump;
    private volatile String calibration = "";

    public M17Station(Context c, String profileId, Host h) { ctx = c.getApplicationContext(); this.profileId = profileId; host = h; }

    public boolean running() { return running; }
    /** A transmission is being decoded (the radio's own audio is held back). */
    public boolean receiving() { return running && current != null; }
    public Heard current() { return current; }
    public synchronized List<Heard> heard() { return new ArrayList<>(heard); }
    public float quality() { return quality; }
    public String calibration() { return calibration; }

    public synchronized String start() {
        if (running) return null;
        float[] coef = load("m17/eq_" + profileId + ".txt");
        if (coef == null) { coef = load("m17/eq_ulefone-armor26ultra.txt"); calibration = "calibrated on the Ulefone Armor 26 Ultra (not on this phone: it may not decode)"; }
        else calibration = profileId.startsWith("ulefone-armor26") ? "calibrated on this phone model" : "calibrated for this phone";
        if (coef == null) return "M17 calibration missing";
        try { codec = new Codec2(Codec2.MODE_3200); } catch (Throwable t) { return "Codec 2 not available: " + t.getMessage(); }
        rx = new M17Receiver(coef, new M17Receiver.Listener() {
            @Override public void started(M17.Lsf l) { begin(l); }
            @Override public void frame(M17.Stream s, float q) { onFrame(s, q); }
            @Override public void lsfFromLich(M17.Lsf l) { Heard h = current; if (h != null) { fill(h, l); host.event("📻 M17: " + h.src + " → " + h.dst + (h.meta != null ? "  “" + h.meta + "”" : "")); host.heard(h.src); host.changed(); } }
            @Override public void ended(int frames, int good, String why) { finish(frames, good, why); }
        });
        running = true; host.changed();
        return null;
    }

    public synchronized void stop() {
        running = false; current = null;
        if (track != null) { try { track.pause(); track.flush(); track.release(); } catch (Throwable ignored) { } track = null; }
        if (codec != null) { codec.close(); codec = null; }
        rx = null; host.changed();
    }

    /** Every receive block (16-bit little-endian mono, 8 kHz), whatever the squelch: the receiver finds the preamble itself. */
    public void feed(byte[] mono, int n) { M17Receiver r = rx; if (running && r != null) r.feed(mono, n); }

    private float[] load(String asset) {
        try (java.io.InputStream in = ctx.getAssets().open(asset)) {
            String[] lines = new String(readAll(in), "UTF-8").trim().split("\\s+");
            if (lines.length != M17Receiver.NCOEF) return null;
            float[] c = new float[lines.length]; for (int i = 0; i < c.length; i++) c[i] = Float.parseFloat(lines[i]); return c;
        } catch (Exception e) { return null; }
    }
    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException { java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int k; while ((k = in.read(b)) > 0) o.write(b, 0, k); return o.toByteArray(); }

    private void fill(Heard h, M17.Lsf l) {
        h.src = l.src(); h.dst = l.dst(); h.meta = l.metaText(); h.encrypted = l.encryption() != 0; h.data = !l.voice();
    }
    private void begin(M17.Lsf l) {
        Heard h = new Heard(System.currentTimeMillis());
        if (l.crcOk) fill(h, l);
        current = h;
        synchronized (this) { heard.add(h); while (heard.size() > 100) heard.remove(0); }
        if (l.crcOk) {
            host.event("📻 M17: " + h.src + " → " + h.dst + (h.meta != null ? "  “" + h.meta + "”" : "") + (h.encrypted ? "  (encrypted: not played)" : ""));
            host.heard(h.src);
        } else host.event("📻 M17: a transmission (its header was damaged; the caller follows from the frames)");
        openTrack();
        host.changed();
    }
    private void onFrame(M17.Stream s, float q) {
        Heard h = current; if (h == null) return;
        h.frames++; h.end = System.currentTimeMillis(); quality = q;
        if (s.lich != null && s.lichErrors <= 2) h.good++;
        if (h.encrypted || h.data) return;
        Codec2 c = codec; AudioTrack t = track; if (c == null || t == null) return;
        try {
            short[] a = c.decode(java.util.Arrays.copyOfRange(s.payload, 0, 8)), b = c.decode(java.util.Arrays.copyOfRange(s.payload, 8, 16));
            short[] pcm = new short[a.length + b.length]; System.arraycopy(a, 0, pcm, 0, a.length); System.arraycopy(b, 0, pcm, a.length, b.length);
            t.write(pcm, 0, pcm.length, AudioTrack.WRITE_NON_BLOCKING);
            host.voice(pcm);
            if (benchDump) try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(ctx.getCacheDir(), "m17_out.raw"), true)) { byte[] by = new byte[pcm.length * 2]; for (int i = 0; i < pcm.length; i++) { by[2 * i] = (byte) pcm[i]; by[2 * i + 1] = (byte) (pcm[i] >> 8); } o.write(by); }
        } catch (Throwable e) { Log.w("M17Station", "voice: " + e); }
        if (h.frames % 12 == 0) host.changed();
    }
    private void finish(int frames, int good, String why) {
        Heard h = current; current = null;
        if (h != null) {
            h.end = System.currentTimeMillis();
            host.event(String.format(Locale.US, "📻 M17 %s ended: %.1f s, %d frames%s", h.src.isEmpty() ? "transmission" : "from " + h.src, (h.end - h.start) / 1000.0, frames,
                frames > 0 && good < frames ? String.format(Locale.US, " (%d %% clean)", 100 * good / frames) : ""));
        }
        AudioTrack t = track; if (t != null) { try { t.stop(); } catch (Throwable ignored) { } }   // plays out what is queued, then quiet
        host.changed();
    }

    private void openTrack() {
        try {
            if (track == null) {
                int min = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min, 8000 * 2)).setTransferMode(AudioTrack.MODE_STREAM).build();
            }
            short[] lead = new short[1200]; track.write(lead, 0, lead.length, AudioTrack.WRITE_NON_BLOCKING);   // 150 ms of cushion for the frames' timing
            track.play();
        } catch (Throwable e) { Log.w("M17Station", "audio: " + e); }
    }
}
