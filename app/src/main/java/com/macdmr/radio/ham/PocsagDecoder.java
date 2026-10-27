/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.List;

/**
 * POCSAG from 8 kHz audio. Tries 512, 1200 and 2400 baud and both bit polarities, then the bit
 * decoder in {@link Pocsag}. A sample below zero is a 1 in the normal polarity. The held-path and
 * decision-feedback front ends feed this same slicer once they have restored the bit shape.
 */
public final class PocsagDecoder {
    private PocsagDecoder() { }
    public static final int RATE = 8000;
    public static final int[] RATES = { 512, 1200, 2400 };

    /** The baud and the pages from the polarity and phase that recovered the most text. */
    public static final class Hit {
        public final int baud;
        public final List<Pocsag.Page> pages;
        public Hit(int baud, List<Pocsag.Page> pages) { this.baud = baud; this.pages = pages; }
    }

    /** Undo a one-pole high-pass of time constant {@code tauSec}. A non-positive tau leaves the audio unchanged. */
    public static short[] restore(short[] x, double tauSec) {
        if (x == null || tauSec <= 0) return x;
        double b = Math.exp(-1.0 / (RATE * tauSec));
        double[] acc = new double[x.length];
        double state = 0, prev = 0, sum = 0;
        for (int i = 0; i < x.length; i++) {
            double s = x[i];
            state = s - b * prev + state;
            prev = s;
            acc[i] = state;
            sum += state;
        }
        double mean = sum / Math.max(1, x.length);
        short[] y = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            double v = acc[i] - mean;
            if (v > 32767) v = 32767;
            else if (v < -32767) v = -32767;
            y[i] = (short) v;
        }
        return y;
    }

    /**
     * Restore only a measured 512 baud preamble. Nearest-rate classification calls a stretched
     * 1200 baud gap "512" (13 samples is closer to 15.6 than to 6.7) and restoration then wipes it.
     */
    public static Hit best(short[] audio, double tauSec) { return best(audio, tauSec, null); }

    /**
     * A measured 512 baud preamble is restored and sliced without the held-path taps
     * (those taps turn the long zero run into another valid address). 1200 and 2400
     * use the taps and are not restored.
     */
    public static Hit best(short[] audio, double tauSec, double[] taps) {
        if (audio == null) return best(audio);
        Clock clock = preamble(audio);
        double expect = (double) RATE / 512.0;
        if (tauSec > 0 && clock != null && Math.abs(clock.spb - expect) < 1.5) {
            Hit fixed = best(restore(audio, tauSec));
            if (fixed.baud == 512 && !fixed.pages.isEmpty()) return fixed;
        }
        if (taps != null && taps.length > 0) {
            Hit held = bestHeld(audio, taps);
            if (held != null && held.pages != null && !held.pages.isEmpty()) return held;
        }
        return best(audio);
    }

    public static Hit best(short[] audio) {
        Hit best = new Hit(1200, java.util.Collections.<Pocsag.Page>emptyList());
        int score = -1;
        if (audio == null) return best;
        Clock clock = preamble(audio);
        if (clock != null) {
            for (int pol : new int[] { 1, -1 }) {
                Hit h = take(clock.baud, follow(audio, clock.spb, clock.phase, pol));
                int n = score(h); if (n > score) { score = n; best = h; }
            }
        }
        for (int baud : RATES) {
            double spb = (double) RATE / baud;
            for (int pol : new int[] { 1, -1 }) {
                for (int s = 0; s < 8; s++) {
                    Hit h = take(baud, slice(audio, spb, s * spb / 8.0, pol));
                    int n = score(h); if (n > score) { score = n; best = h; }
                }
            }
        }
        return best;
    }

    private static Hit take(int baud, int[] bits) { return new Hit(baud, Pocsag.decode(bits)); }
    private static int score(Hit h) {
        int n = 0;
        for (Pocsag.Page p : h.pages) n += p.ricUnreadable ? 1 : p.text.length() * 10;
        return n;
    }

    /** A run of alternating bits: the baud from the gap, and the middle of the first bit. */
    static final class Clock { final int baud; final double spb, phase; Clock(int baud, double spb, double phase) { this.baud = baud; this.spb = spb; this.phase = phase; } }

    /** True when the latest audio still looks like a preamble, so a scan should not step. */
    public static boolean hearingPreamble(short[] audio) { return audio != null && preamble(audio) != null; }

    /** The held-path taps, then the same slicer. Null taps leaves the audio alone. */
    public static Hit bestHeld(short[] audio, double[] taps) {
        if (taps == null || audio == null || audio.length < 800) return best(audio);
        double[] x = HeldPath.signed(audio, audio.length);
        double[] y = HeldPath.equalise(HeldPath.matched(HeldPath.resample6(x)), taps);
        short[] back = new short[y.length / 6];
        for (int i = 0; i < back.length; i++) {
            double v = y[i * 6];
            if (v > 32767) v = 32767; else if (v < -32767) v = -32767;
            back[i] = (short) v;
        }
        return best(back);
    }

    static Clock preamble(short[] audio) {
        int last = -1, crosses = 0, start = -1;
        double sum = 0;
        for (int i = 1; i < audio.length; i++) {
            if ((audio[i] < 0) == (audio[i - 1] < 0)) continue;
            if (last >= 0) {
                int d = i - last;
                if (d >= 2 && d <= 20) {
                    sum += d; crosses++;
                    if (start < 0) start = last;
                    if (crosses >= 80) break;
                } else { sum = 0; crosses = 0; start = -1; }
            }
            last = i;
        }
        if (crosses < 40) return null;
        double spb = sum / crosses;
        int baud = RATES[0]; double err = Math.abs(RATE / spb - baud);
        for (int r : RATES) { double e = Math.abs(RATE / spb - r); if (e < err) { err = e; baud = r; } }
        return new Clock(baud, spb, start + spb / 2.0);
    }

    /** One threshold for the whole batch: the mean of the sync word, held until the next sync. */
    static int[] follow(short[] audio, double spb, double phase, int pol) {
        return follow(audio, spb, phase, pol, false);
    }

    /**
     * {@code runningMean} is the old slicer: a mean of the last 32 bits, updated every bit. A long run of zeros
     * walks that mean and can turn the address into a different valid codeword. Kept so the regression test can
     * show the generator's 512 baud file doing that, and the per-batch threshold not doing it.
     */
    static int[] follow(short[] audio, double spb, double phase, int pol, boolean runningMean) {
        int cap = (int) (audio.length / Math.max(1.0, spb)) + 2;
        int[] bits = new int[Math.max(1, cap)];
        int n = 0; double pos = phase; int guard = 0;
        Level level = new Level();
        while (pos >= 0 && pos < audio.length && n < bits.length) {
            if (runningMean) level.push(sampleAt(audio, pos));
            bits[n++] = decide(audio, pos, pol, level);
            pos += spb;
            if (guard > 0) { guard--; continue; }
            if (n >= 32 && hamming(bits, n - 32) <= 2) {
                double start = pos - 32 * spb;
                double shift = relock(audio, start, spb, pol);
                if (!runningMean) {
                    double sum = 0;
                    for (int i = 0; i < 32; i++) sum += sampleAt(audio, start + shift + i * spb);
                    level.mean = sum / 32.0;
                    level.freeze = true;
                }
                for (int i = 0; i < 32; i++) bits[n - 32 + i] = decide(audio, start + shift + i * spb, pol, level);
                pos = start + shift + 32 * spb;
                guard = 32;
            }
        }
        return java.util.Arrays.copyOf(bits, n);
    }

    private static double relock(short[] audio, double start, double spb, int pol) {
        double best = 0; int bestErr = 99;
        Level zero = new Level();
        for (int step = -8; step <= 8; step++) {
            double d = step * spb / 20.0;
            int err = 0;
            for (int i = 0; i < 32; i++) {
                int got = decide(audio, start + d + i * spb, pol, zero);
                int want = (Pocsag.SYNC >>> (31 - i)) & 1;
                if (got != want) err++;
            }
            if (err < bestErr) { bestErr = err; best = d; }
        }
        return best;
    }

    private static double sampleAt(short[] audio, double pos) {
        int k = (int) pos;
        return k >= 0 && k < audio.length ? audio[k] : 0;
    }

    private static int hamming(int[] bits, int at) {
        int err = 0;
        for (int i = 0; i < 32; i++) if (bits[at + i] != ((Pocsag.SYNC >>> (31 - i)) & 1)) err++;
        return err;
    }

    /** One bit per sample-clock step. A negative sample (after polarity) is a 1. */
    static int[] slice(short[] audio, double spb, double phase, int pol) {
        int n = 0;
        for (double t = phase + spb / 2; t < audio.length; t += spb) n++;
        int[] bits = new int[n];
        Level level = new Level();
        int i = 0;
        for (double t = phase + spb / 2; t < audio.length; t += spb) bits[i++] = decide(audio, t, pol, level);
        return bits;
    }

    /** A slow baseline, about the last 32 bits. Re-centred when a sync word is found. */
    static final class Level {
        double mean;
        boolean freeze;
        final double[] box = new double[32];
        int n, at;
        double sum;
        void push(double v) {
            if (freeze) return;
            if (n == box.length) sum -= box[at]; else n++;
            box[at] = v; sum += v; at = (at + 1) % box.length;
            mean = n == 0 ? 0 : sum / n;
        }
    }

    private static int decide(short[] audio, double pos, int pol, Level level) {
        return (pol * (sampleAt(audio, pos) - level.mean) < 0) ? 1 : 0;
    }
}
