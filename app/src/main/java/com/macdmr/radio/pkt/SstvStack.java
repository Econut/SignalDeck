/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines several receptions of the same SSTV picture (the ISS repeats its slides): each one is aligned to the
 * reception with the most lines (a shift in rows and pixels, found on the blurred brightness), then every pixel is the
 * mean of the receptions that have it, or the median of three or more (which also removes streaks). Noise is random
 * and the picture is not, so it averages out. Rows no reception has stay black. Pure Java: ARGB ints.
 */
public final class SstvStack {
    private SstvStack() { }

    /** The result: the pixels, how many receptions were used, and per picture whether it could be aligned. */
    public static final class Result {
        public final int[] argb; public final int used; public final boolean[] aligned; public final int[] dy, dx;
        /** The reference picture (shift 0), and per picture whether the line-up is clear (false: a guess, check it). */
        public int ref; public boolean[] sure;
        Result(int[] a, int u, boolean[] al, int[] y, int[] x) { argb = a; used = u; aligned = al; dy = y; dx = x; }
    }

    static boolean[] validRows(int[] p, int w, int h) {
        boolean[] v = new boolean[h];
        for (int y = 0; y < h; y++) { int m = 0; for (int x = 0; x < w; x++) { int c = p[y * w + x]; m = Math.max(m, Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255))); } v[y] = m > 12; }
        return v;
    }
    static float[] blurLuma(int[] p, int w, int h) {
        float[] l = new float[w * h], o = new float[w * h];
        for (int i = 0; i < w * h; i++) { int c = p[i]; l[i] = 0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255); }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0; int n = 0;
            for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) { int yy = y + j, xx = x + i; if (yy < 0 || yy >= h || xx < 0 || xx >= w) continue; s += l[yy * w + xx]; n++; }
            o[y * w + x] = s / n;
        }
        return o;
    }

    /**
     * Best {dy, dx, correlation*1000, overlapping rows, best correlation*1000 at a row shift more than 4 away} to put
     * picture b on reference a: a's row r = b's row r + dy. The last value says how clear the match is.
     */
    static int[] align(float[] la, boolean[] va, float[] lb, boolean[] vb, int w, int h) {
        int[] best = { 0, 0, -2000, 0, -2000 }; int margin = 8; int[] perDy = new int[2 * h]; java.util.Arrays.fill(perDy, -2000);
        for (int dy = -h + 16; dy <= h - 16; dy++) {
            List<int[]> rows = new ArrayList<>();
            for (int r = 0; r < h; r++) { int rb = r - dy; if (rb >= 0 && rb < h && va[r] && vb[rb]) rows.add(new int[] { r, rb }); }
            if (rows.size() < 16) continue;
            for (int dx = -6; dx <= 6; dx++) {
                double sa = 0, sb = 0, n = 0;
                for (int[] rr : rows) for (int x = margin; x < w - margin; x++) { sa += la[rr[0] * w + x]; sb += lb[rr[1] * w + x + dx]; n++; }
                double ma = sa / n, mb = sb / n, ab = 0, aa = 0, bb = 0;
                for (int[] rr : rows) for (int x = margin; x < w - margin; x++) { double p = la[rr[0] * w + x] - ma, q = lb[rr[1] * w + x + dx] - mb; ab += p * q; aa += p * p; bb += q * q; }
                int c = (int) Math.round(1000 * ab / Math.sqrt(aa * bb + 1e-9));
                perDy[dy + h] = Math.max(perDy[dy + h], c);
                if (c > best[2] || (c == best[2] && rows.size() > best[3])) { best = new int[] { dy, dx, c, rows.size(), -2000 }; }
            }
        }
        for (int d = 0; d < perDy.length; d++) if (Math.abs(d - h - best[0]) > 4) best[4] = Math.max(best[4], perDy[d]);
        return best;
    }

    /** Combine pictures of size w x h; those that do not correlate at least {@code minCorr} with the reference are left out. */
    public static Result combine(List<int[]> pics, int w, int h, double minCorr) {
        int n = pics.size(); boolean[][] v = new boolean[n][]; int ref = 0, most = -1;
        for (int i = 0; i < n; i++) { v[i] = validRows(pics.get(i), w, h); int c = 0; for (boolean b : v[i]) if (b) c++; if (c > most) { most = c; ref = i; } }
        float[] lr = blurLuma(pics.get(ref), w, h);
        int[] dy = new int[n], dx = new int[n]; boolean[] ok = new boolean[n], sure = new boolean[n]; ok[ref] = true; sure[ref] = true;
        for (int i = 0; i < n; i++) {
            if (i == ref) continue;
            int[] a = align(lr, v[ref], blurLuma(pics.get(i), w, h), v[i], w, h);
            dy[i] = a[0]; dx[i] = a[1]; ok[i] = a[2] >= minCorr * 1000;
            // clear only with enough shared rows, a sideways shift inside the search range and a peak well above any other row shift
            sure[i] = ok[i] && a[3] >= 24 && Math.abs(a[1]) < 6 && a[2] - a[4] >= 50;
        }
        Result r = place(pics, w, h, dy, dx, ok); r.ref = ref; r.sure = sure; return r;
    }

    /** Draw the pictures with the given shifts (picture i's row y - dy[i], pixel x + dx[i] lands on y, x); {@code use} false leaves one out. */
    public static Result place(List<int[]> pics, int w, int h, int[] dy, int[] dx, boolean[] ok) {
        int n = pics.size(); boolean[][] v = new boolean[n][]; for (int i = 0; i < n; i++) v[i] = validRows(pics.get(i), w, h);
        int[] out = new int[w * h]; int used = 0; for (boolean b : ok) if (b) used++;
        int[] rs = new int[n], gs = new int[n], bs = new int[n];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (!ok[i]) continue;
                int yb = y - dy[i], xb = x + dx[i];
                if (yb < 0 || yb >= h || xb < 0 || xb >= w || !v[i][yb]) continue;
                int c = pics.get(i)[yb * w + xb]; rs[k] = (c >> 16) & 255; gs[k] = (c >> 8) & 255; bs[k] = c & 255; k++;
            }
            out[y * w + x] = k == 0 ? 0xFF000000 : 0xFF000000 | (pick(rs, k) << 16) | (pick(gs, k) << 8) | pick(bs, k);
        }
        Result r = new Result(out, used, ok, dy, dx); r.sure = ok.clone(); return r;
    }
    private static int pick(int[] a, int k) {
        if (k < 3) { int s = 0; for (int i = 0; i < k; i++) s += a[i]; return s / k; }
        int[] c = java.util.Arrays.copyOf(a, k); java.util.Arrays.sort(c); return k % 2 == 1 ? c[k / 2] : (c[k / 2 - 1] + c[k / 2]) / 2;
    }
}
