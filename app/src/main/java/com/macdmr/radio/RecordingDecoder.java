/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import com.macdmr.radio.pkt.AFSKDecoder;
import com.macdmr.radio.pkt.Ax25;
import com.macdmr.radio.pkt.SstvContinuous;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodes a recording (a pass recording, REC, or any 16-bit PCM WAV) after the fact: SSTV with the continuous Robot 36
 * receiver, APRS with the same AFSK decoder and windows as live reception. It runs on its own instances, as fast as the
 * CPU allows, and never touches the radio, the speaker or the live decoders; decoded packets are only reported (never
 * digipeated, gated, acknowledged or handed to the TNC). Pure Java: host-tested.
 */
public final class RecordingDecoder {
    public static final int SR = 8000;

    public interface Listener {
        default void progress(int percent) { }
        /** A picture ended {@code atMs} into the recording. */
        default void picture(int[] argb, int width, int height, int lines, boolean complete, long atMs) { }
        /** A packet (plausible AX.25 frame, FCS good) that started about {@code atMs} into the recording. */
        default void packet(byte[] frame, long atMs) { }
    }

    private volatile boolean cancelled;
    public void cancel() { cancelled = true; }
    public boolean cancelled() { return cancelled; }

    /** A 16-bit PCM WAV at any rate, mono or stereo (channels mixed), as 8 kHz mono. */
    public static short[] read8k(byte[] all) throws IOException {
        if (all.length < 44 || !new String(all, 0, 4, "US-ASCII").equals("RIFF") || !new String(all, 8, 4, "US-ASCII").equals("WAVE")) throw new IOException("not a WAV file");
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int ch = 1, rate = 8000, bits = 16, fmt = 1, pos = 12, dataAt = -1, dataLen = 0;
        while (pos + 8 <= all.length) {
            String id = new String(all, pos, 4, "US-ASCII"); int len = bb.getInt(pos + 4);
            if (id.equals("fmt ")) { fmt = bb.getShort(pos + 8); ch = Math.max(1, bb.getShort(pos + 10)); rate = bb.getInt(pos + 12); bits = bb.getShort(pos + 22); }
            if (id.equals("data")) { dataAt = pos + 8; dataLen = len < 0 || len > all.length - dataAt ? all.length - dataAt : len; break; }
            if (len < 0) break;
            pos += 8 + len + (len & 1);
        }
        if (dataAt < 0) throw new IOException("no audio data in the file");
        if ((fmt != 1 && fmt != -2) || bits != 16) throw new IOException("only 16-bit PCM WAV files can be decoded");
        if (rate < 8000) throw new IOException("the sample rate is below 8 kHz");
        int frames = dataLen / (2 * ch);
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) { int at = dataAt + i * 2 * ch, sum = 0; for (int c = 0; c < ch; c++) sum += bb.getShort(at + 2 * c); mono[i] = (short) (sum / ch); }
        return to8k(mono, rate);
    }

    /**
     * Mono PCM at any rate to 8 kHz. Above 8 kHz it is low-pass filtered first (4th-order Butterworth at 3.4 kHz, two
     * biquads), so a 44.1 / 48 kHz recording does not fold its hiss above 4 kHz into the SSTV and AFSK tones, then
     * resampled by linear interpolation. 8 kHz is returned as it is.
     */
    public static short[] to8k(short[] x, int rate) {
        if (rate == SR) return x;
        if (rate < SR) throw new IllegalArgumentException("the sample rate is below 8 kHz");
        double[] y = new double[x.length]; for (int i = 0; i < x.length; i++) y[i] = x[i];
        biquadLowPass(y, rate, 3400, 0.5412); biquadLowPass(y, rate, 3400, 1.3066);
        int out = (int) ((long) x.length * SR / rate);
        short[] s = new short[out];
        for (int o = 0; o < out; o++) {
            double t = (double) o * rate / SR; int i = (int) t; double f = t - i;
            double v = i + 1 < y.length ? y[i] * (1 - f) + y[i + 1] * f : y[Math.min(i, y.length - 1)];
            s[o] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
        }
        return s;
    }
    private static void biquadLowPass(double[] y, int rate, double fc, double q) {      // RBJ cookbook, in place
        double w = 2 * Math.PI * fc / rate, cs = Math.cos(w), al = Math.sin(w) / (2 * q), a0 = 1 + al;
        double b0 = (1 - cs) / 2 / a0, b1 = (1 - cs) / a0, b2 = b0, a1 = -2 * cs / a0, a2 = (1 - al) / a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < y.length; i++) { double x0 = y[i], v = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x0; y2 = y1; y1 = v; y[i] = v; }
    }

    /** Robot 36 pictures in the recording (continuous receiver: no VIS needed, partial pictures kept). Returns how many. */
    public int sstv(short[] s, Listener l) {
        final int[] count = { 0 }; final long[] at = { 0 };
        SstvContinuous c = new SstvContinuous(SR, new SstvContinuous.Listener() {
            @Override public void onLive(int[] argb, int w, int h, boolean locked, int lines) { }
            @Override public void onPicture(int[] argb, int w, int h, int lines, boolean complete) { count[0]++; l.picture(argb.clone(), w, h, lines, complete, at[0] * 1000 / SR); }
        });
        int blk = 512, lastPct = -1;                                           // the live block size (64 ms)
        short[] b = new short[blk];
        for (int i = 0; i < s.length && !cancelled; i += blk) {
            int n = Math.min(blk, s.length - i); System.arraycopy(s, i, b, 0, n);
            at[0] = i + n; c.process(b, n);
            int pct = (int) (100L * (i + n) / s.length); if (pct != lastPct) { lastPct = pct; l.progress(pct); }
        }
        if (!cancelled) c.flush();
        return count[0];
    }

    /** APRS / AX.25 packets in the recording, in the live windows (2 s plus 1 s overlap); a copy within 4 s is the same packet. */
    public int aprs(short[] s, Listener l) {
        final int W = 2 * SR, O = SR; int count = 0, lastPct = -1;
        Map<String, Long> seen = new HashMap<>();
        for (int start = 0; start < s.length && !cancelled; start += W) {
            int from = Math.max(0, start - O), to = Math.min(s.length, start + W);
            short[] win = Arrays.copyOfRange(s, from, to);
            for (byte[] f : AFSKDecoder.decode(up6(win))) {
                if (!Ax25.plausible(f)) continue;
                long ms = from * 1000L / SR; String key = Arrays.toString(f); Long prev = seen.get(key);
                if (prev != null && ms - prev <= 4000) continue;
                seen.put(key, ms); count++; l.packet(f, ms);
            }
            int pct = (int) (100L * to / s.length); if (pct != lastPct) { lastPct = pct; l.progress(pct); }
        }
        return count;
    }

    /** The AFSK decoder works at 48 kHz: 6x linear upsampling, as live reception does. */
    static short[] up6(short[] in) {
        short[] out = new short[in.length * 6];
        for (int i = 0; i < in.length; i++) { int a = in[i], b = i + 1 < in.length ? in[i + 1] : in[i]; for (int k = 0; k < 6; k++) out[i * 6 + k] = (short) (a + (b - a) * k / 6); }
        return out;
    }

    /** "20261023_040312_pass.wav" → the recording's start (local time), or -1. */
    public static long startFromName(String name) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{8}_\\d{6})").matcher(name);
            if (!m.find()) return -1;
            return new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).parse(m.group(1)).getTime();
        } catch (Exception e) { return -1; }
    }

    /** mm:ss into the recording. */
    public static String clock(long ms) { long s = ms / 1000; return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60); }
}
