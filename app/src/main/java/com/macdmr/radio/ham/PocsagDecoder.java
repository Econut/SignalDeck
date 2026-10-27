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
        for (int baud : RATES) {
            double spb = (double) RATE / baud;
            int steps = 8;
            for (int pol : new int[] { 1, -1 }) {
                for (int s = 0; s < steps; s++) {
                    double phase = s * spb / steps;
                    int[] bits = slice(audio, spb, phase, pol);
                    List<Pocsag.Page> pages = Pocsag.decode(bits);
                    int n = 0;
                    for (Pocsag.Page p : pages) n += p.text.length();
                    if (n > score) { score = n; best = new Hit(baud, pages); }
                }
            }
        }
        return best;
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
