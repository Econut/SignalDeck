/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The draft profile from two real reports, cut to the lines it reads: the Atom XL (2026-10-28, radio behind Agold's USB
 * bridge and HAL) and the W6+ (docs/w6/reports, an SA818-class module on a UART). Where the report can know a field,
 * the W6 draft must agree with its built-in profile.
 */
public class DraftProfileTest {
    private static String res(String name) throws Exception {
        return new String(DraftProfileTest.class.getResourceAsStream("/report/" + name).readAllBytes(), "UTF-8");
    }

    private static Map<String, DraftProfile.Field> draft(String report) {
        Map<String, DraftProfile.Field> m = new HashMap<>();
        for (DraftProfile.Field f : DraftProfile.fields(report)) m.put(f.name, f);
        return m;
    }

    @Test public void atomXl() throws Exception {
        Map<String, DraftProfile.Field> d = draft(res("atomxl_excerpt.txt"));
        assertEquals("com.agold.intercom", d.get("stockApp").value);
        assertTrue(d.get("protocol").value.startsWith("mcu68 inside the Agold 7E 96 69 envelope"));
        assertEquals(DraftProfile.HIGH, d.get("protocol").confidence);
        assertTrue(d.get("protocol").evidence.contains("DMR006.U4A.V067"));
        assertEquals("/dev/ttyACM0 (USB serial to a bridge MCU)", d.get("controlPort").value);
        assertEquals("none (USB CDC-ACM)", d.get("baud").value);
        assertEquals("vendor HAL vendor.mediatek.hardware.aguiextmodule@1.0 (the stock app calls it over HwBinder)", d.get("transport").value);
        assertEquals("USB Audio card 1, 32000 Hz, 2 channels (no SignalDeck backend for it yet)", d.get("audio").value);
        assertTrue(d.get("powerNode").value.startsWith("/sys/bus/platform/drivers/mcu/mcu_power"));
        assertTrue(d.get("powerNode").evidence.endsWith("read it as off"));
        assertEquals(DraftProfile.UNKNOWN, d.get("pttKey").confidence);              // nobody pressed during that capture
        String acts = d.get("pttActions").value;
        assertTrue(acts.contains("android.intent.action.PTT.down") && acts.contains("android.intent.action.PTT.up"));
        assertFalse(acts.contains("ro.agui"));                                         // property names are not broadcasts
        assertTrue(d.get("sepolicy").value.startsWith("allow untrusted_app ttyACM_device chr_file"));
        assertEquals(DraftProfile.LOW, d.get("sepolicy").confidence);               // the HAL holds the port: the label alone is not enough
        assertEquals("none", d.get("serialLib").value);
        assertNotNull(d.get("usb"));
    }

    @Test public void w6AgreesWithItsProfile() throws Exception {
        Map<String, DraftProfile.Field> d = draft(res("w6_excerpt.txt"));
        DeviceProfile p = DeviceProfile.ANYSECU_W6;
        assertEquals(p.stockApp, d.get("stockApp").value);
        assertEquals(p.protocol, d.get("protocol").value);
        assertEquals(DraftProfile.HIGH, d.get("protocol").confidence);
        assertEquals(p.controlPort, d.get("controlPort").value);
        assertEquals(String.valueOf(p.baud), d.get("baud").value);
        assertEquals(p.sepolicy[0] + "; " + p.sepolicy[1], d.get("sepolicy").value);
        assertNull(d.get("usb"));                                                        // no USB bridge: no USB note, no transport line
        assertNull(d.get("transport"));
        assertEquals(DraftProfile.UNKNOWN, d.get("powerNode").confidence);            // gpio322 came from the stock app's code by hand: a gap the draft names
    }

    @Test public void broadcastsDropPropertyNames() {
        assertEquals("android.intent.action.PTT.down android.intent.action.PTT.up",
                DraftProfile.broadcasts("android.intent.action.PTT.down ro.agui.ptt.hshookkey android.intent.action.PTT.up persist.sys.ptt"));
        assertEquals("", DraftProfile.broadcasts("-"));
    }

    @Test public void lastSectionForTheSummaryFile() {
        String rep = "==== Summary ====\nold\n\n==== Radio probe ====\nx\n\n==== Summary ====\nnew verdict\n";
        assertEquals("\nnew verdict\n", DeviceReport.lastSection(rep, "Summary"));
        assertNull(DeviceReport.lastSection(rep, "Draft profile"));
    }

    @Test public void bridgePortLine() {
        String[] p = AgoldUsb.port("x\nbridge port: /dev/ttyACM0 u:object_r:ttyACM_device:s0 held by /vendor/bin/hw/vendor.mediatek.hardware.aguiextmodule@1.0-service\n");
        assertArrayEquals(new String[] { "/dev/ttyACM0", "u:object_r:ttyACM_device:s0", "/vendor/bin/hw/vendor.mediatek.hardware.aguiextmodule@1.0-service" }, p);
        assertNull(AgoldUsb.port("devices it holds open: /dev/null\n"));
    }
}
