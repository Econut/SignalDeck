package com.macdmr.radio;

import static org.junit.Assert.*;

import com.macdmr.radio.ham.M17Receiver;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/** The decision-feedback receiver's self-calibration store: pending fit, check on a later call, keep or drop, reset. */
public class DfeCalTest {
    static short[] wav(String res) throws Exception {
        byte[] b = DfeCalTest.class.getResourceAsStream(res).readAllBytes(); int n = (b.length - 44) / 2; short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[44 + 2 * i] & 0xFF) | (b[45 + 2 * i] << 8)); return s;
    }
    static int[] symbols(JSONObject part) throws Exception { String s = part.getString("symbols"); int[] o = new int[s.length()]; for (int i = 0; i < o.length; i++) o[i] = 2 * (s.charAt(i) - '0') - 3; return o; }

    @Test public void keepsAFitOnlyWhenALaterCallConfirmsIt() throws Exception {
        short[] audio = wav("/m17/m17_rx_w6.wav");
        JSONObject m = new JSONObject(new String(DfeCalTest.class.getResourceAsStream("/m17/m17_rx_w6.json").readAllBytes(), "UTF-8"));
        JSONObject p0 = m.getJSONArray("parts").getJSONObject(0), p1 = m.getJSONArray("parts").getJSONObject(1);
        float[] good = java.util.Arrays.copyOf(M17Receiver.calibrate(audio, p1.getInt("start8k"), symbols(p1), M17Receiver.LEARN_FEEDBACK), M17Receiver.NCOEF);
        float[] none = new float[M17Receiver.NCOEF];                                    // a table that decodes nothing
        short[] firstCall = java.util.Arrays.copyOfRange(audio, 0, p1.getInt("start8k") - 4000);
        File dir = Files.createTempDirectory("dfecal").toFile();
        DfeCal cal = new DfeCal(dir, "test-phone");

        assertFalse(cal.onCall(null, 5, firstCall, "N0CALL", -999, none));                // too few frames: nothing pending
        assertTrue(cal.line(), cal.line().contains("5 confirmed frames"));
        assertFalse(cal.onCall(null, 0, firstCall, "N0CALL", -999, none));                // and still nothing to check against

        assertFalse(cal.onCall(good, 95, firstCall, "N0CALL", -999, none));                // a fit waits
        assertTrue(cal.line().contains("waiting"));
        assertFalse("same station, no signal reading: not kept", cal.onCall(null, 0, firstCall, "N0CALL", -999, none));
        assertNull(cal.accepted());

        assertFalse(cal.onCall(good, 95, firstCall, "N0CALL", -999, none));
        assertTrue("another station, decodes better: kept", cal.onCall(null, 0, firstCall, "DMRTST", -999, none));
        assertArrayEquals(good, cal.accepted(), 1e-6f);
        assertTrue(cal.line(), cal.line().startsWith("fitted on this phone"));

        assertFalse(cal.onCall(none, 95, firstCall, "N0CALL", -999, good));                // a fit that decodes nothing is dropped
        assertFalse(cal.onCall(null, 0, firstCall, "DMRTST", -999, good));
        assertTrue(cal.line(), cal.line().contains("no link setup"));
        assertArrayEquals("the kept table stays", good, cal.accepted(), 1e-6f);

        cal.reset();
        assertNull(cal.accepted());
        assertFalse(cal.acceptedFile().exists());
    }
}
