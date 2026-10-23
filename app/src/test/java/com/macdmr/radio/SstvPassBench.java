/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;

/**
 * Bench: run the app's SSTV recording decoder over real pass recordings (ISS_WAV_DIR, *_pass.wav) and write every
 * picture it finds to ISS_OUT as PNG, with a line per picture (time in the pass, lines, complete) and a noise score.
 */
public class SstvPassBench {
    @Test public void decodePasses() throws Exception {
        String dir = System.getenv("ISS_WAV_DIR"), out = System.getenv("ISS_OUT");
        assumeTrue(dir != null && out != null);
        new File(out).mkdirs();
        File[] fs = new File(dir).listFiles((d, n) -> n.endsWith("_pass.wav")); java.util.Arrays.sort(fs);
        for (File f : fs) {
            short[] s = RecordingDecoder.read8k(java.nio.file.Files.readAllBytes(f.toPath()));
            final String base = f.getName().replace("_pass.wav", "");
            final int[] k = { 0 };
            int n = new RecordingDecoder().sstv(s, new RecordingDecoder.Listener() { @Override public void picture(int[] argb, int w, int h, int lines, boolean complete, long atMs) {
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(out, base + "_" + (k[0]++) + (complete ? "" : "_partial") + ".ppm"))) {
                    o.write(("P6\n" + w + " " + h + "\n255\n").getBytes());
                    byte[] px = new byte[w * h * 3]; for (int i = 0; i < w * h; i++) { px[3 * i] = (byte) (argb[i] >> 16); px[3 * i + 1] = (byte) (argb[i] >> 8); px[3 * i + 2] = (byte) argb[i]; }
                    o.write(px);
                    System.out.printf("%s: picture at %s, %d lines%s, noise %.1f%n", base, RecordingDecoder.clock(atMs), lines, complete ? " (complete)" : "", noise(argb, w, h, lines));
                } catch (Exception e) { throw new RuntimeException(e); }
            } });
            System.out.println(base + ": " + n + " pictures");
        }
    }

    /** Mean absolute difference between horizontally neighbouring pixels (luma) over the received lines: higher = noisier. */
    static double noise(int[] argb, int w, int h, int lines) {
        double s = 0; int c = 0;
        for (int y = 0; y < h; y++) for (int x = 1; x < w; x++) {
            int a = argb[y * w + x], b = argb[y * w + x - 1];
            if ((a & 0xFFFFFF) == 0 && (b & 0xFFFFFF) == 0) continue;
            s += Math.abs(luma(a) - luma(b)); c++;
        }
        return c == 0 ? 0 : s / c;
    }
    static double luma(int p) { return 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255); }
}
