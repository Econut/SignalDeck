/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.macdmr.radio.Firmware;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Armor held-path taps against the 18 independent R-F clips. */
public class HeldPathTest {
    @Test public void rrcMatchesTheResearchFilter() {
        double[] h = HeldPath.rrc(0.5, 8, 10);
        assertEquals(81, h.length);
        assertEquals(-0.003195702904062073, h[0], 1e-12);
        assertEquals(0.35945293202760803, HeldPath.rrcCentre(), 1e-12);
        assertEquals(-0.003195702904062073, h[80], 1e-12);
    }

    @Test public void stubTagIsABenchImage() {
        assertTrue(Firmware.stubTag("DMR003.RG10.V022"));
        assertTrue(!Firmware.stubTag("DMR003.UV4T.V022"));
        assertTrue(!Firmware.stubTag(""));
        assertTrue(!Firmware.stubTag("DMR003.RG10.V023"));
    }

    @Test public void independentClips() throws Exception {
        String dirEnv = System.getenv("M17_RF_DIR");
        Path dir = Paths.get(dirEnv != null ? dirEnv : "C:\\Users\\Joshua\\AppData\\Local\\Temp\\fw1718\\rf");
        Assume.assumeTrue("R-F clips are not on this machine", Files.isDirectory(dir) && Files.exists(dir.resolve("expected.json")));
        double[] taps = taps();
        JSONArray clips = new JSONObject(new String(Files.readAllBytes(dir.resolve("expected.json")), "UTF-8")).getJSONArray("clips");
        assertEquals(18, clips.length());
        for (int i = 0; i < clips.length(); i++) {
            JSONObject c = clips.getJSONObject(i);
            String name = c.getString("name");
            short[] pcm = M17Test.wav(Files.newInputStream(dir.resolve(name + ".wav")));
            List<HeldPath.FrameHit> hits = HeldPath.decode(pcm, taps);
            boolean crc = false;
            int ns = 0, exact = 0;
            Map<Integer, byte[]> want = new HashMap<>();
            JSONArray frames = c.getJSONArray("frames");
            for (int k = 0; k < frames.length(); k++) {
                JSONObject f = frames.getJSONObject(k);
                want.put(f.getInt("fn"), hex(f.getString("hex")));
            }
            for (HeldPath.FrameHit h : hits) {
                if (h.link != null) {
                    if (h.link.crcOk && c.getString("src").equals(h.link.src()) && c.getString("dst").equals(h.link.dst())) crc = true;
                } else if (h.stream != null) {
                    ns++;
                    byte[] w = want.get(h.stream.fn);
                    if (w != null && java.util.Arrays.equals(w, h.stream.payload)) exact++;
                }
            }
            double pct = ns == 0 ? 0 : 100.0 * exact / ns;
            System.out.printf("%-18s LSF %s  stream %d  payload-exact %d  (%.1f%%)%n", name, crc, ns, exact, pct);
            int[] ref = REF.get(name);
            assertTrue(name + " link-setup CRC", crc);
            assertEquals(name + " stream frames", ref[0], ns);
            assertEquals(name + " payload-exact", ref[1], exact);
        }
    }

    /** Equalise-first counts from tools/m17-research/heldpath_rf_score.py on these clips (2026-10-24). */
    private static final java.util.Map<String, int[]> REF = new java.util.HashMap<>();
    static {
        int[][] rows = {
            {74, 71}, {77, 74}, {74, 71}, {76, 76}, {74, 73}, {73, 72}, {76, 75}, {74, 74}, {74, 74},
            {74, 72}, {75, 72}, {77, 71}, {74, 74}, {18, 18}, {77, 76}, {75, 74}, {73, 72}, {75, 75}
        };
        String[] names = {
            "m17_d1900_v0_0", "m17_d1900_v0_1", "m17_d1900_v0_2",
            "m17_d1900_v10_0", "m17_d1900_v10_1", "m17_d1900_v10_2",
            "m17_d1900_v20_0", "m17_d1900_v20_1", "m17_d1900_v20_2",
            "m17_d2400_v0_0", "m17_d2400_v0_1", "m17_d2400_v0_2",
            "m17_d2400_v10_0", "m17_d2400_v10_1", "m17_d2400_v10_2",
            "m17_d2400_v20_0", "m17_d2400_v20_1", "m17_d2400_v20_2"
        };
        for (int i = 0; i < names.length; i++) REF.put(names[i], rows[i]);
    }

    private static double[] taps() throws Exception {
        Path asset = Paths.get("src/main/assets/m17/held_ulefone-armor26ultra.txt");
        if (!Files.exists(asset)) asset = Paths.get("app/src/main/assets/m17/held_ulefone-armor26ultra.txt");
        String[] lines = new String(Files.readAllBytes(asset), "UTF-8").trim().split("\\s+");
        assertEquals(HeldPath.TAPS, lines.length);
        double[] t = new double[lines.length];
        for (int i = 0; i < t.length; i++) t[i] = Double.parseDouble(lines[i]);
        return t;
    }

    private static byte[] hex(String s) {
        byte[] o = new byte[s.length() / 2];
        for (int i = 0; i < o.length; i++) o[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return o;
    }
}
