/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import org.junit.Test;

/** Writes bench WAVs for the injection tool when the BENCH_OUT environment variable names a folder (otherwise does nothing). */
public class WriteBenchWavs {
    @Test public void write() throws Exception {
        String dir = System.getenv("BENCH_OUT"); if (dir == null) return;
        wav(dir + "/psk31_test.wav", Psk31.encode("PSK31 BENCH TEST de DMR Radio 73", 1234));
        wav(dir + "/rtty_test.wav", Rtty.encode("RTTY BENCH TEST 599 73", 2125, 1000));
        wav(dir + "/aprs_demo.wav", aprsDemo());
    }
    /** Demo APRS traffic for screenshots: made-up DEMO stations around Denver (no real callsigns or places of a user). */
    static short[] aprsDemo() {
        String[][] pk = {
            { "DEMO-9", "APRS", "!3944.40N/10459.20W>Mobile demo, 146.520 simplex" },
            { "DEMO-7", "APRS", "!3945.10N/10501.30W[On foot near downtown" },
            { "DEMOWX", "APRS", "@231030z3943.80N/10458.60W_270/008g015t058r000p000P000h40b10180Demo weather" },
            { "DEMO-1", "APRS", ">SignalDeck demo station" },
            { "DEMO-2", "APRS", "=3940.25N/10503.75W-Home station, monitoring 144.390" },
            { "DEMO-9", "APRS", ":Q0DEM-7  :Hello from the demo, see you on 146.520{01" },
        };
        java.util.List<short[]> parts = new java.util.ArrayList<>(); int total = 0;
        for (String[] k : pk) {
            String[] c = k[0].split("-");
            byte[] f = com.macdmr.radio.pkt.Ax25.buildUiFrame(k[1], 0, c[0], c.length > 1 ? Integer.parseInt(c[1]) : 0, new String[] { "WIDE1-1" }, k[2].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            short[] a = com.macdmr.radio.pkt.AFSKGenerator.generateAFSKWithNRZI(f, 80);
            parts.add(a); parts.add(new short[8000 * 2]); total += a.length + 16000;
        }
        short[] out = new short[total + 8000]; int o = 8000;
        for (short[] a : parts) { System.arraycopy(a, 0, out, o - 8000 + 8000, Math.min(a.length, out.length - o)); o += a.length; }
        return out;
    }
    static void wav(String path, short[] pcm) throws Exception {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + pcm.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + pcm.length * 2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(pcm.length * 2);
        for (short s : pcm) b.putShort(s);
        java.nio.file.Files.write(java.nio.file.Paths.get(path), b.array());
    }
}
