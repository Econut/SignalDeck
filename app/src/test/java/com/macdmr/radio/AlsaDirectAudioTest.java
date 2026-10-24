/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class AlsaDirectAudioTest {
    @Test public void vendorServiceStaysTheDefault() {
        assertEquals("vendor", AlsaDirectAudio.choose("auto", true, true));     // stock ROM: unchanged
        assertEquals("direct", AlsaDirectAudio.choose("auto", false, true));    // custom ROM / GSI on a phone with a recipe
        assertEquals("vendor", AlsaDirectAudio.choose("auto", false, false));   // no recipe: the old behaviour (and its error)
        assertEquals("vendor", AlsaDirectAudio.choose("direct", true, false));  // forcing needs a recipe
        assertEquals("direct", AlsaDirectAudio.choose("direct", true, true));
        assertEquals("vendor", AlsaDirectAudio.choose("vendor", false, true));
    }

    @Test public void commandLineQuotesEveryControl() {
        DeviceProfile.DirectAudio d = DeviceProfile.ARMOR_26_ULTRA.directAudio;
        String cap = AlsaDirectAudio.command("/data/x/sdalsa", true, d);
        assertTrue(cap, cap.startsWith("exec '/data/x/sdalsa' cap -c 0 -d 11 -r 8000 -n 2 -p 256 -k 4 'UL2_CH1 I2S2_CH1=1'"));
        assertTrue(cap, cap.contains(" -- 'UL2_CH1 I2S2_CH1=0' 'UL2_CH2 I2S2_CH2=0'"));
        String play = AlsaDirectAudio.command("/data/x/sdalsa", false, d);
        assertTrue(play, play.contains(" play -c 0 -d 8 ") && play.contains("'I2S1_CH1 DL8_CH1=1'") && play.contains("-- 'I2S1_CH1 DL8_CH1=0'"));
        assertEquals("'it'\\''s'", AlsaDirectAudio.quote("it's"));
    }

    @Test public void monoBecomesStereoDuplicated() {
        byte[] mono = { 1, 2, 3, 4 }; byte[] st = new byte[8];
        assertEquals(8, AlsaDirectAudio.stereo(mono, 4, st));
        assertArrayEquals(new byte[] { 1, 2, 1, 2, 3, 4, 3, 4 }, st);
    }

    @Test public void armor26RecipeIsValidAndListEntriesAreChecked() throws Exception {
        assertTrue(DeviceProfile.ARMOR_26_ULTRA.directAudio.valid());
        assertNull(DeviceProfile.XPLORE_1.directAudio);
        JSONObject ok = new JSONObject("{\"card\":0,\"captureDevice\":11,\"playbackDevice\":8,\"rxOn\":[\"A B=1\"],\"rxOff\":[],\"txOn\":[\"C=Low_Jitter\"],\"txOff\":[]}");
        assertEquals(11, DeviceProfile.DirectAudio.fromJson(ok).captureDevice);
        JSONObject bad = new JSONObject("{\"card\":0,\"captureDevice\":11,\"playbackDevice\":8,\"rxOn\":[\"A=1'; reboot; '\"],\"rxOff\":[],\"txOn\":[],\"txOff\":[]}");
        try { DeviceProfile.DirectAudio.fromJson(bad); fail("a control with shell characters must be refused"); } catch (org.json.JSONException expected) { }
    }
}
