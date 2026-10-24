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
