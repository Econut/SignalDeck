/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SstvStackTest {
    static final int W = 320, H = 240;
    /** A test picture with detail at every scale, like a photo: smoothed random blobs plus a frame. */
    static int[] picture() {
        Random r = new Random(42); int[] p = new int[W * H]; float[][] ch = new float[3][W * H];
        for (int k = 0; k < 3; k++) {
            float[] f = ch[k]; for (int i = 0; i < f.length; i++) f[i] = r.nextFloat();
            for (int pass = 0; pass < 3; pass++) {                // a few box blurs: blobs a few pixels across
                float[] g = new float[f.length];
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { float sum = 0; int n = 0; for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) { int yy = y + j, xx = x + i; if (yy >= 0 && yy < H && xx >= 0 && xx < W) { sum += f[yy * W + xx]; n++; } } g[y * W + x] = sum / n; }
                f = g;
            }
            float mn = 1, mx = 0; for (float v : f) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
            for (int i = 0; i < f.length; i++) f[i] = (f[i] - mn) / (mx - mn);
            ch[k] = f;
        }
        for (int i = 0; i < W * H; i++) p[i] = 0xFF000000 | ((int) (30 + 220 * ch[0][i]) << 16) | ((int) (30 + 220 * ch[1][i]) << 8) | (int) (30 + 220 * ch[2][i]);
        return p;
    }
    /** A noisy reception covering rows [from, to), shifted by dy rows and dx pixels (as a partial picture is placed). */
    static int[] reception(int[] pic, int from, int to, int dy, int dx, double noise, long seed) {
        Random r = new Random(seed); int[] o = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int ys = y - dy, xs = x + dx;
            if (ys < from || ys >= to || ys < 0 || ys >= H) { o[y * W + x] = 0xFF000000; continue; }
            int c = pic[ys * W + Math.max(0, Math.min(W - 1, xs))];
            int rr = clamp(((c >> 16) & 255) + r.nextGaussian() * noise), gg = clamp(((c >> 8) & 255) + r.nextGaussian() * noise), bb = clamp((c & 255) + r.nextGaussian() * noise);
            o[y * W + x] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
        return o;
    }
    static int clamp(double v) { return (int) Math.max(13, Math.min(255, Math.round(v))); }
    static double rms(int[] a, int[] b, int y0, int y1) {
        double s = 0; int n = 0;
        for (int y = y0; y < y1; y++) for (int x = 10; x < W - 10; x++) { int p = a[y * W + x], q = b[y * W + x]; for (int k = 0; k < 24; k += 8) { double d = ((p >> k) & 255) - ((q >> k) & 255); s += d * d; n++; } }
        return Math.sqrt(s / n);
    }

    @Test public void threeNoisyReceptionsCombineIntoACleanerPicture() {
        int[] pic = picture();
        List<int[]> rec = new ArrayList<>();
        rec.add(reception(pic, 0, 240, 0, 0, 60, 1));          // a complete one
        rec.add(reception(pic, 80, 240, 0, 2, 60, 2));        // partial, 2 px off
        rec.add(reception(pic, 60, 240, -3, 0, 60, 3));       // partial, placed 3 rows off
        SstvStack.Result r = SstvStack.combine(rec, W, H, 0.3);
        assertEquals(3, r.used);
        System.out.printf("alignment: picture 2 dy %d dx %d, picture 3 dy %d dx %d%n", r.dy[1], r.dx[1], r.dy[2], r.dx[2]);
        assertEquals("pixel shift of picture 2", -2, r.dx[1]); assertEquals("row shift of picture 3", 3, r.dy[2]);
        double single = rms(rec.get(0), pic, 100, 230), stacked = rms(r.argb, pic, 100, 230);
        System.out.printf("noise: one reception %.1f, combined %.1f%n", single, stacked);
        assertTrue("combining must lower the noise: " + single + " -> " + stacked, stacked < single * 0.75);
    }

    @Test public void aDifferentPictureIsLeftOut() {
        int[] pic = picture(), other = new int[W * H];
        Random r = new Random(9); for (int i = 0; i < other.length; i++) other[i] = 0xFF000000 | (r.nextInt(200) + 20) << 8;
        List<int[]> rec = new ArrayList<>();
        rec.add(reception(pic, 0, 240, 0, 0, 40, 1)); rec.add(reception(other, 0, 240, 0, 0, 40, 2));
        SstvStack.Result s = SstvStack.combine(rec, W, H, 0.3);
        assertEquals(1, s.used); assertFalse(s.aligned[1]);
    }
}
