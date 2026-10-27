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
    private static int score(Hit h) { int n = 0; for (Pocsag.Page p : h.pages) n += p.text.length(); return n; }

    /** A run of alternating bits: the baud from the gap, and the middle of the first bit. */
    static final class Clock { final int baud; final double spb, phase; Clock(int baud, double spb, double phase) { this.baud = baud; this.spb = spb; this.phase = phase; } }

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

    /** Slice, and each time a sync word is recognised nudge the clock onto it. */
    static int[] follow(short[] audio, double spb, double phase, int pol) {
        int cap = (int) (audio.length / Math.max(1.0, spb)) + 2;
        int[] bits = new int[Math.max(1, cap)];
        int n = 0; double pos = phase; int guard = 0;
        while (pos >= 0 && pos < audio.length && n < bits.length) {
            bits[n++] = bitAt(audio, pos, pol);
            pos += spb;
            if (guard > 0) { guard--; continue; }
            if (n >= 32 && hamming(bits, n - 32) <= 2) {
                double start = pos - 32 * spb;
                double shift = relock(audio, start, spb, pol);
                for (int i = 0; i < 32; i++) bits[n - 32 + i] = bitAt(audio, start + shift + i * spb, pol);
                pos = start + shift + 32 * spb;
                guard = 32;
            }
        }
        return java.util.Arrays.copyOf(bits, n);
    }

    private static int bitAt(short[] audio, double pos, int pol) {
        int k = (int) pos;
        if (k < 0 || k >= audio.length) return 0;
        return (pol * audio[k] < 0) ? 1 : 0;
    }

    private static double relock(short[] audio, double start, double spb, int pol) {
        double best = 0; int bestErr = 99;
        for (int step = -8; step <= 8; step++) {
            double d = step * spb / 20.0;
            int err = 0;
            for (int i = 0; i < 32; i++) {
                int got = bitAt(audio, start + d + i * spb, pol);
                int want = (Pocsag.SYNC >>> (31 - i)) & 1;
                if (got != want) err++;
            }
            if (err < bestErr) { bestErr = err; best = d; }
        }
        return best;
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
        int i = 0;
        for (double t = phase + spb / 2; t < audio.length; t += spb) {
            int k = (int) t;
            int sample = k >= 0 && k < audio.length ? audio[k] : 0;
            bits[i++] = (pol * sample < 0) ? 1 : 0;
        }
        return bits;
    }
}
