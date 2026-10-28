package com.macdmr.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.macdmr.radio.dsp.SoftSquelch;
import org.junit.Test;

/** The W6 plays the received audio itself and gates it with its own Soft SQ table (measured 2026-10-28). */
public class W6SoftSqTest {
    @Test public void theW6PlaysItselfWithItsOwnNoiseTable() {
        DeviceProfile w6 = DeviceProfile.ANYSECU_W6;
        assertTrue(w6.appPlayback); assertFalse(w6.softSqInModule); assertFalse(w6.subaudible);
        assertEquals(4, w6.fixedModuleVolume);
        double[] t = w6.softSqThresholds;
        assertEquals(9, t.length);
        for (int i = 1; i < 9; i++) assertTrue("level " + (i + 1) + " stricter than " + i, t[i] < t[i - 1]);
        for (double v : t) assertTrue("the gate can close again above every threshold", Math.min(v * 1.3, w6.softSqCloseCap) >= v);
        assertTrue("closes below the lowest static reading (12.8)", w6.softSqCloseCap < 12.8);
        DeviceProfile c = w6.copy(true, false);
        assertTrue(c.appPlayback); assertFalse(c.subaudible); assertEquals(t, c.softSqThresholds);
        assertFalse(DeviceProfile.ARMOR_26_ULTRA.appPlayback); assertTrue(DeviceProfile.ARMOR_26_ULTRA.subaudible);
        assertEquals(null, DeviceProfile.ARMOR_26_ULTRA.softSqThresholds);
    }

    @Test public void withoutToneDetectionACtcssChannelUsesTheNoiseDetector() {
        SoftSquelch s = new SoftSquelch(); s.toneDetect = false;
        s.configure(true, 5, 1, 1);                                      // a CTCSS channel
        assertTrue(s.describe(), s.describe().startsWith("noise"));
        SoftSquelch a = new SoftSquelch(); a.configure(true, 5, 1, 1);
        assertTrue(a.describe(), a.describe().startsWith("tone"));
    }
}
