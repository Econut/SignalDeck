/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.FileOutputStream;
import java.util.Random;

/**
 * Bench: writes one FT8 slot (8 kHz mono WAV, signals 0.5 s in, light noise) to FT8_OUT for the DIAG inject command or
 * a transmit test. FT8_MSGS: messages separated by "|", each optionally "@Hz" (default 1000, 1600, 2200 Hz ...).
 */
public class Ft8BenchWav {
    @Test public void write() throws Exception {
        String out = System.getenv("FT8_OUT");
        assumeTrue(out != null);
        String msgs = System.getenv("FT8_MSGS") == null ? "CQ K1ABC FN42|K1ABC G4ABC IO91@1600" : System.getenv("FT8_MSGS");
        int n = Ft8.RATE * 15; float[] x = new float[n]; Random r = new Random(1);
        for (int i = 0; i < n; i++) x[i] = (float) (0.01 * r.nextGaussian());
        String[] list = msgs.split("\\|");
        for (int k = 0; k < list.length; k++) {
            String m = list[k]; double hz = 1000 + 600 * k;
            int at = m.lastIndexOf('@'); if (at > 0) { hz = Double.parseDouble(m.substring(at + 1)); m = m.substring(0, at); }
            float[] w = Ft8.synth(Ft8.tones(Ft8Message.pack(m, null)), hz, Ft8.RATE);
            for (int i = 0; i < w.length; i++) x[Ft8.RATE / 2 + i] += 0.25f * w[i] / list.length;
        }
        byte[] b = new byte[44 + 2 * n];
        java.nio.ByteBuffer h = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + 2 * n).put("WAVEfmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(Ft8.RATE).putInt(Ft8.RATE * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(2 * n);
        for (int i = 0; i < n; i++) h.putShort((short) Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767))));
        try (FileOutputStream f = new FileOutputStream(out)) { f.write(b); }
    }
}
