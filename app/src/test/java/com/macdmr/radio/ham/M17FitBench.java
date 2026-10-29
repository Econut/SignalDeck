/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assume.assumeTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Bench: fits the stock-path M17 table for a new phone model on one recorded HackRF transmission and counts the
 * payload-exact frames on another. M17_FIT_SYMS: fit only that many leading symbols. M17_FIT_JSON / M17_TEST_JSON: the M17Test json layout, with the wav next to it;
 * M17_FIT_PART / M17_TEST_PART: which part (default 0); M17_FIT_OUT writes the table (assets/m17/eq_<profile>.txt).
 * Skipped unless M17_FIT_JSON is set.
 */
public class M17FitBench {
    @Test public void fitAndScore() throws Exception {
        String fit = System.getenv("M17_FIT_JSON"); assumeTrue(fit != null);
        String test = System.getenv("M17_TEST_JSON") == null ? fit : System.getenv("M17_TEST_JSON");
        int fp = env("M17_FIT_PART", 0), tp = env("M17_TEST_PART", 0);
        JSONObject fm = json(fit), tm = json(test);
        short[] fa = wavNextTo(fit, fm), ta = wavNextTo(test, tm);
        JSONObject fpart = fm.getJSONArray("parts").getJSONObject(fp), tpart = tm.getJSONArray("parts").getJSONObject(tp);
        if (fpart.optInt("polarity", 1) < 0) invert(fa);
        if (tpart.optInt("polarity", 1) < 0) invert(ta);
        int feedback = env("M17_FIT_FB", M17Receiver.LEARN_FEEDBACK);
        int start = fpart.getInt("start8k"), scan = env("M17_FIT_SCAN", 0), bestStart = start;   // scan: also try start +- scan samples (the fit itself searches +-20)
        int[] fsym = M17Test.symbols(fpart);
        int keep = env("M17_FIT_SYMS", fsym.length);                    // fit only the first symbols (a phone whose audio clock drifts); the rest count as unknown
        for (int k = keep; k < fsym.length; k++) fsym[k] = 0;
        float[] cal = null;
        for (int s = start - scan; s <= start + scan; s += 30) {
            float[] c = M17Receiver.calibrate(fa, s, fsym, feedback);
            if (scan > 0) System.out.printf("  start %d: MSE %.3f%n", s, c[M17Receiver.NCOEF]);
            if (cal == null || c[M17Receiver.NCOEF] < cal[M17Receiver.NCOEF]) { cal = c; bestStart = s; }
        }
        System.out.printf("fit MSE %.3f (feedback %d, start %d, clock %+.0f ppm)%n", cal[M17Receiver.NCOEF], feedback, bestStart, cal[M17Receiver.NCOEF + 1]);
        String out = System.getenv("M17_FIT_OUT");
        if (out != null) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < M17Receiver.NCOEF; i++) sb.append(cal[i]).append('\n'); java.nio.file.Files.write(java.nio.file.Paths.get(out), sb.toString().getBytes("UTF-8")); }
        final List<byte[]> payloads = new ArrayList<>(); final List<String> events = new ArrayList<>();
        M17Receiver rx = new M17Receiver(java.util.Arrays.copyOf(cal, M17Receiver.NCOEF), new M17Receiver.Listener() {
            public void started(M17.Lsf l) { events.add("start " + l.src() + " " + l.crcOk); }
            public void frame(M17.Stream s, float q) { payloads.add(s.payload); }
            public void lsfFromLich(M17.Lsf l) { events.add("lich " + l.src()); }
            public void ended(int f, int g, String why) { events.add("end " + f + " " + g + " " + why); }
        });
        rx.feed(ta, ta.length); rx.feed(new short[8000], 8000);
        int exact = M17Test.exactPayloads(payloads, tpart);
        System.out.println(events + "\nexact payloads: " + exact + " of " + tpart.getJSONArray("payloads").length() + " (" + test + " part " + tp + ")");
    }

    private static int env(String k, int d) { String v = System.getenv(k); return v == null ? d : Integer.parseInt(v); }
    private static JSONObject json(String p) throws Exception { return new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(p)), "UTF-8")); }
    private static short[] wavNextTo(String jsonPath, JSONObject m) throws Exception {
        java.io.File f = new java.io.File(new java.io.File(jsonPath).getParentFile(), m.getString("wav"));
        try (java.io.InputStream in = new java.io.FileInputStream(f)) { return M17Test.wav(in); }
    }
    private static void invert(short[] a) { for (int i = 0; i < a.length; i++) a[i] = (short) Math.max(-32767, -a[i]); }
}
