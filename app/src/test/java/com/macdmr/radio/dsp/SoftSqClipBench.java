/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.dsp;

import static org.junit.Assume.assumeTrue;

import com.macdmr.radio.RecordingDecoder;

import org.junit.Test;

import java.io.File;
import java.util.Locale;

/**
 * Bench for tuning the Soft SQ levels on real receptions. Record clips with MON open (DIAG {@code rxclip}: 8 kHz mono,
 * before any app squelch) of signals a listener calls readable, and of plain static, then run with
 * {@code SQ_CLIP_DIR=<dir>}. Files whose name starts with {@code static} should stay closed; every other clip should open.
 * Per clip it prints how much of the clip each level 1-9 lets through (the real detector) and the smoothed noise
 * readings; then a sweep of open thresholds (the detector's gate, replayed on those readings) over all clips, so a new
 * level 1 threshold is chosen from measurements.
 */
public class SoftSqClipBench {
    private static final int RATE = 8000, WINDOW = RATE / 4;      // the detector's 250 ms window

    @Test public void sweep() throws Exception {
        String dir = System.getenv("SQ_CLIP_DIR");
        assumeTrue(dir != null);
        File[] fs = new File(dir).listFiles((d, n) -> n.toLowerCase(Locale.US).endsWith(".wav"));
        assumeTrue(fs != null && fs.length > 0);
        java.util.Arrays.sort(fs);
        double[][] sm = new double[fs.length][]; boolean[][] quiet = new boolean[fs.length][];
        System.out.println("clip                          level 1..9: % of windows open          smoothed p10/p50/p90");
        for (int f = 0; f < fs.length; f++) {
            short[] s = RecordingDecoder.read8k(java.nio.file.Files.readAllBytes(fs[f].toPath()));
            int w = s.length / WINDOW; sm[f] = new double[w]; quiet[f] = new boolean[w];
            StringBuilder lv = new StringBuilder();
            for (int level = 1; level <= 9; level++) {
                NoiseSquelchDetector d = new NoiseSquelchDetector(RATE); int open = 0;
                for (int i = 0; i < w; i++) {
                    byte[] b = bytes(s, i * WINDOW);
                    d.process(b, b.length, level);
                    if (d.isSignal()) open++;
                    if (level == 1) { sm[f][i] = d.smoothed(); quiet[f][i] = energy(s, i * WINDOW) < WINDOW * 2500.0; }
                }
                lv.append(String.format(Locale.US, "%4d", w == 0 ? 0 : 100 * open / w));
            }
            double[] sorted = sm[f].clone(); java.util.Arrays.sort(sorted);
            System.out.printf(Locale.US, "%-30s%s   %5.1f %5.1f %5.1f%n", trim(fs[f].getName()), lv, pct(sorted, 10), pct(sorted, 50), pct(sorted, 90));
        }
        System.out.println();
        System.out.println("open threshold sweep (close = min(open x 1.3, 46)): % of windows open per clip");
        StringBuilder head = new StringBuilder(String.format("%-8s", "open"));
        for (File f : fs) head.append(String.format("%-14s", trim(f.getName()).substring(0, Math.min(13, trim(f.getName()).length()))));
        System.out.println(head);
        for (double t = 16; t <= 46; t += 2) {
            StringBuilder row = new StringBuilder(String.format(Locale.US, "%-8.0f", t));
            for (int f = 0; f < fs.length; f++) row.append(String.format(Locale.US, "%-14d", gatePercent(sm[f], quiet[f], t)));
            System.out.println(row);
        }
        System.out.println("(current open thresholds, level 1..9: 38 35 32 29 27 24 22 19 16)");
    }

    /** The detector's gate (NoiseSquelchDetector.process) replayed on recorded smoothed readings. */
    static int gatePercent(double[] smoothed, boolean[] quiet, double open) {
        double close = Math.min(open * 1.3, 46); boolean signal = false; int n = 0;
        for (int i = 0; i < smoothed.length; i++) {
            if (quiet[i]) { }
            else if (!signal && smoothed[i] < open) signal = true;
            else if (signal && smoothed[i] > close) signal = false;
            if (signal) n++;
        }
        return smoothed.length == 0 ? 0 : 100 * n / smoothed.length;
    }

    private static byte[] bytes(short[] s, int from) {
        byte[] b = new byte[WINDOW * 2];
        for (int i = 0; i < WINDOW; i++) { short x = s[from + i]; b[2 * i] = (byte) x; b[2 * i + 1] = (byte) (x >> 8); }
        return b;
    }
    private static double energy(short[] s, int from) { double e = 0; for (int i = 0; i < WINDOW; i++) e += (double) s[from + i] * s[from + i]; return e; }
    private static double pct(double[] sorted, int p) { return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, sorted.length * p / 100)]; }
    private static String trim(String n) { return n.length() > 28 ? n.substring(0, 28) : n; }
}
