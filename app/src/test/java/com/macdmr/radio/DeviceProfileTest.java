/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class DeviceProfileTest {
    @Test public void p8RecognitionNeverEnablesHardwareOrMatchesOtherP8Models() {
        DeviceProfile p = DeviceProfile.detectBuiltIn("uniwa p8 mt6765", false, false);
        assertSame(DeviceProfile.UNIWA_P8, p);
        assertEquals("/dev/ttysWK0", p.controlPort);
        assertEquals("/dev/ttyS0", p.pcmPort);
        assertEquals(230400, p.pcmBaud);
        assertFalse(p.supported); assertFalse(p.verified); assertTrue(p.offlineOnly());
        assertTrue(p.copy(true, true).offlineOnly());
        assertSame(DeviceProfile.UNKNOWN, DeviceProfile.detectBuiltIn("huawei p8", false, false));
        assertSame(DeviceProfile.UNKNOWN, DeviceProfile.detectBuiltIn("uniwa p80", false, false));
        assertSame(DeviceProfile.UNIWA_P8, DeviceProfile.detectBuiltIn("uniwa p8 mt6765", true, true));
    }

    @Test public void remoteProfilesCannotTurnP8HardwareOn() throws Exception {
        org.json.JSONObject list = new org.json.JSONObject("{\"profiles\":[{\"id\":\"uniwa-p8\",\"revision\":999,\"match\":{\"model\":[\"uniwa p8\"]},\"name\":\"unsafe replacement\",\"controlPort\":\"/dev/ttysWK0\",\"baud\":57600,\"audio\":\"UART_PCM\",\"supported\":true,\"verified\":true}]}");
        ProfileRegistry.Selection s = ProfileRegistry.select(DeviceProfile.UNIWA_P8, list, null, false, null, "2026-10-27");
        assertSame(DeviceProfile.UNIWA_P8, s.profile);
        assertFalse(s.profile.supported); assertTrue(s.profile.offlineOnly());
    }
    @Test public void armor20wtIsNotTakenForTheArmor26() {
        // Armor 20WT report (beta.18, 2026-10-24): same dmr009 node as the Armor 26 Ultra, detected as one before
        DeviceProfile p = DeviceProfile.detectBuiltIn("ulefone armor 20wt armor_20wt", true, false);
        assertSame(DeviceProfile.ARMOR_20WT, p);
        assertEquals("/dev/ttyS1", p.controlPort);
        assertEquals(57600, p.baud);
        assertFalse("untested on hardware: no automatic take-over, no transmit", p.verified);
        assertTrue(p.serialLib.endsWith("libserial_port_app.so"));
        assertEquals("ulefone-armor20wt", p.id);
    }

    @Test public void otherPhonesUnchanged() {
        assertSame(DeviceProfile.ARMOR_26_ULTRA, DeviceProfile.detectBuiltIn("ulefone armor 26 ultra armor_26_ultra", true, false));
        assertSame(DeviceProfile.ARMOR_26_ULTRA, DeviceProfile.detectBuiltIn("some phone x", true, false));
        assertSame(DeviceProfile.XPLORE_1, DeviceProfile.detectBuiltIn("blackview xplore 1 walkie talkie xplore_1_wt", false, true));
        assertSame(DeviceProfile.UNKNOWN, DeviceProfile.detectBuiltIn("some phone x", false, false));
    }

    @Test public void serialWrapperFollowsTheLibrarysSymbols() {
        String armor20 = "      JNI com.serial.port.SerialPort.close()\n      JNI com.serial.port.SerialPort.open()\n";
        String armor26 = "      JNI com.pri.prizeinterphone.serial.port.SerialPort.open()\n      JNI com.pri.prizeinterphone.serial.port.SerialPort.close()\n";
        assertEquals(VendorSerial.PLAIN, VendorSerial.wrapperFor(armor20));
        assertEquals(VendorSerial.PRI, VendorSerial.wrapperFor(armor26));
        assertEquals(VendorSerial.PRI, VendorSerial.wrapperFor(""));          // registers its methods itself: the known one
        assertTrue(VendorSerial.haveWrapper(armor20));
        assertFalse(VendorSerial.haveWrapper("      JNI com.wonder.serial.SerialPort.open()\n"));
    }
}
