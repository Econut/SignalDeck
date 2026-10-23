/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interoperability with WSJT-X: decodes real off-air FT8 recordings (12 kHz WAV + the WSJT-X decode list next to each,
 * e.g. ft8_lib's test/wav set) after resampling them to this app's 8 kHz. Runs only when FT8_WAV_DIR points at them.
 */
public class Ft8RecordingsTest {
    static float[] wav8k(File f) throws Exception {
        byte[] b = Files.readAllBytes(f.toPath());
        int rate = (b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16;
        int data = 12; while (!(b[data] == 'd' && b[data + 1] == 'a' && b[data + 2] == 't' && b[data + 3] == 'a')) data += 8 + ((b[data + 4] & 0xFF) | (b[data + 5] & 0xFF) << 8 | (b[data + 6] & 0xFF) << 16);
        int n = ((b[data + 4] & 0xFF) | (b[data + 5] & 0xFF) << 8 | (b[data + 6] & 0xFF) << 16 | (b[data + 7] & 0xFF) << 24) / 2;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = (short) ((b[data + 8 + 2 * i] & 0xFF) | (b[data + 9 + 2 * i] << 8)) / 32768.0;
        if (rate == 8000) { float[] o = new float[n]; for (int i = 0; i < n; i++) o[i] = (float) x[i]; return o; }
        assertEquals(12000, rate);
        // 12 kHz -> 8 kHz: windowed-sinc low-pass at 3.6 kHz evaluated at every 1.5th input sample
        int m = (int) (n * 8000L / 12000); float[] o = new float[m]; int taps = 48; double fc = 3600.0 / 12000;
        for (int k = 0; k < m; k++) {
            double t = k * 1.5; int c = (int) Math.floor(t); double s = 0;
            for (int j = c - taps; j <= c + taps; j++) {
                if (j < 0 || j >= n) continue;
                double d = t - j, sinc = d == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * d) / (Math.PI * d), w = 0.42 + 0.5 * Math.cos(Math.PI * d / (taps + 1)) + 0.08 * Math.cos(2 * Math.PI * d / (taps + 1));
                s += x[j] * sinc * w;
            }
            o[k] = (float) s;
        }
        return o;
    }

    @Test public void decodesWhatWsjtxDecoded() throws Exception {
        String dir = System.getenv("FT8_WAV_DIR");
        assumeTrue(dir != null && new File(dir).isDirectory());
        int expected = 0, found = 0, extra = 0; double dtErr = 0, fErr = 0; int matched = 0;
        for (File w : new File(dir).listFiles((d, nm) -> nm.endsWith(".wav"))) {
            File t = new File(w.getPath().replace(".wav", ".txt"));
            if (!t.exists()) continue;
            Set<String> want = new HashSet<>(); java.util.Map<String, double[]> where = new java.util.HashMap<>();
            for (String line : new String(Files.readAllBytes(t.toPath()), StandardCharsets.US_ASCII).split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 6) continue;
                int tilde = line.indexOf('~'); if (tilde < 0) continue;
                String msg = line.substring(tilde + 1).trim().replaceAll("\\s+", " ");
                want.add(msg); where.put(msg, new double[] { Double.parseDouble(p[2]), Double.parseDouble(p[3]) });
            }
            float[] x = wav8k(w);
            List<Ft8.Decode> got = Ft8.decode(x, x.length, new Ft8Message.Hashes());
            int f = 0;
            for (Ft8.Decode d : got) {
                if (want.contains(d.text)) { f++; double[] ref = where.get(d.text); dtErr += d.dtS - ref[0]; fErr += d.freqHz - ref[1]; matched++; }
                else { extra++; System.out.println("  not in the WSJT-X list: " + d); }
            }
            System.out.println(w.getName() + ": " + f + " of " + want.size() + " (+" + (got.size() - f) + " others)");
            expected += want.size(); found += f;
        }
        System.out.printf("FT8 recordings: %d of %d WSJT-X decodes (%.0f %%), %d not in its list; mean dt error %+.3f s, freq error %+.2f Hz%n",
            found, expected, 100.0 * found / expected, extra, dtErr / matched, fErr / matched);
        assertTrue(found > expected / 2);
    }
}
