/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio.ham;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits the 41 held-path taps from frames the receiver already accepted. A frame is a target only when
 * re-encoding it disagrees with the sliced audio on at most 5 % of its symbols. A fit needs 20 such
 * frames. The new taps stay pending until a later call, scored on the same recording with both sets,
 * locks on its own and is at least as clean, and is either 6 dB weaker or another station.
 */
public final class HeldFit {
    public static final int L = 20;
    public static final int TAPS = 41;
    public static final int FRAME = 192;
    public static final int MIN_FRAMES = 20;
    /** At most 5 % of the 192 symbols. */
    public static final int MAX_MISMATCH = FRAME * 5 / 100;

    private HeldFit() { }

    /** One accepted frame: unequalised symbol-rate samples, 20 of context on each side, and the 192 symbols re-encoded from the decode. */
    public static final class Frame {
        public final double[] window;
        public final int[] known;
        public Frame(double[] window, int[] known) { this.window = window; this.known = known; }
    }

    /** How many of the receiver's sliced symbols disagree with the re-encoded frame, after a gain and offset. */
    public static int mismatches(double[] sliced, int[] known) {
        if (sliced == null || known == null || sliced.length < FRAME || known.length < FRAME) return FRAME;
        double sy = 0, sk = 0, syk = 0, skk = 0;
        for (int i = 0; i < FRAME; i++) {
            double y = sliced[i], k = known[i];
            sy += y; sk += k; syk += y * k; skk += k * k;
        }
        double den = FRAME * skk - sk * sk;
        if (Math.abs(den) < 1e-6) return FRAME;
        double g = (FRAME * syk - sy * sk) / den;
        double off = (sy - g * sk) / FRAME;
        if (Math.abs(g) < 1e-6) return FRAME;
        int bad = 0;
        for (int i = 0; i < FRAME; i++) if (slice((sliced[i] - off) / g) != known[i]) bad++;
        return bad;
    }

    public static boolean targetOk(double[] sliced, int[] known) { return mismatches(sliced, known) <= MAX_MISMATCH; }

    static int slice(double z) {
        int best = 3; double d = 1e9;
        for (int s : new int[] {-3, -1, 1, 3}) { double e = Math.abs(z - s); if (e < d) { d = e; best = s; } }
        return best;
    }

    /** 41 taps, or null when there are fewer than {@link #MIN_FRAMES} usable frames. */
    public static double[] fit(List<Frame> frames) {
        int n = 0;
        for (Frame f : frames) if (f != null && f.window != null && f.window.length >= L + FRAME) n++;
        if (n < MIN_FRAMES) return null;
        double[][] ata = new double[TAPS][TAPS];
        double[] atb = new double[TAPS];
        for (Frame f : frames) {
            if (f == null || f.window == null || f.window.length < L + FRAME) continue;
            double[] z = normalised(f.window, f.known);
            for (int i = 0; i < FRAME; i++) {
                double[] row = new double[TAPS];
                for (int k = 0; k < TAPS; k++) row[k] = z[i + TAPS - 1 - k];
                double b = f.known[i];
                for (int r = 0; r < TAPS; r++) {
                    atb[r] += row[r] * b;
                    for (int c = 0; c < TAPS; c++) ata[r][c] += row[r] * row[c];
                }
            }
        }
        double ridge = 0;
        for (int i = 0; i < TAPS; i++) ridge += ata[i][i];
        ridge = ridge / TAPS * 1e-4;
        for (int i = 0; i < TAPS; i++) ata[i][i] += ridge;
        return solve(ata, atb);
    }

    /** The 192 equalised symbols of one frame. */
    public static double[] equalise(double[] window, int[] known, double[] taps) {
        double[] z = normalised(window, known);
        double[] eq = new double[FRAME];
        for (int i = 0; i < FRAME; i++) {
            double s = 0;
            for (int k = 0; k < TAPS; k++) s += taps[k] * z[i + TAPS - 1 - k];
            eq[i] = s;
        }
        return eq;
    }

    private static double[] normalised(double[] window, int[] known) {
        double sy = 0, sk = 0, syk = 0, skk = 0;
        for (int i = 0; i < FRAME; i++) {
            double y = window[L + i], k = known[i];
            sy += y; sk += k; syk += y * k; skk += k * k;
        }
        double den = FRAME * skk - sk * sk;
        double g = Math.abs(den) < 1e-6 ? 1 : (FRAME * syk - sy * sk) / den;
        double off = (sy - g * sk) / FRAME;
        if (Math.abs(g) < 1e-3) g = 1;
        double[] z = new double[window.length];
        for (int i = 0; i < window.length; i++) z[i] = (window[i] - off) / g;
        return z;
    }

    /** Pending taps replace the live ones when they lock on their own, are at least as clean, and the call is 6 dB weaker or another station. */
    public static boolean keep(int pendingExact, int liveExact, boolean pendingLocked, int fitDbm, int checkDbm, String fitSrc, String checkSrc) {
        if (!pendingLocked || pendingExact < liveExact) return false;
        boolean weaker = checkDbm <= fitDbm - 6;
        boolean other = fitSrc != null && checkSrc != null && !fitSrc.isEmpty() && !fitSrc.equals(checkSrc);
        return weaker || other;
    }

    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int r = 0; r < n; r++) { System.arraycopy(a[r], 0, m[r], 0, n); m[r][n] = b[r]; }
        for (int c = 0; c < n; c++) {
            int piv = c; double best = Math.abs(m[c][c]);
            for (int r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > best) { best = Math.abs(m[r][c]); piv = r; }
            if (best < 1e-12) return null;
            double[] tmp = m[c]; m[c] = m[piv]; m[piv] = tmp;
            double div = m[c][c];
            for (int k = c; k <= n; k++) m[c][k] /= div;
            for (int r = 0; r < n; r++) if (r != c) {
                double f = m[r][c];
                for (int k = c; k <= n; k++) m[r][k] -= f * m[c][k];
            }
        }
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = m[i][n];
        return w;
    }

    /** Payload-exact stream frames. {@code payloads} lines up with {@code frames}; a null payload is a link setup and is skipped. */
    public static int exactStreams(List<Frame> frames, double[] taps, List<byte[]> payloads) {
        int exact = 0;
        for (int i = 0; i < frames.size() && i < payloads.size(); i++) {
            byte[] want = payloads.get(i);
            if (want == null) continue;
            Frame f = frames.get(i);
            double[] eq = equalise(f.window, f.known, taps);
            float[] sym = new float[FRAME];
            for (int k = 0; k < FRAME; k++) sym[k] = (float) eq[k];
            M17.Stream s = M17.decodeStream(sym, 8);
            if (s != null && s.payload != null && java.util.Arrays.equals(s.payload, want)) exact++;
        }
        return exact;
    }
}
