/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The Atom XL's USB bridge: which phones have it, where the USB controller's cable mode lives, the summary line. */
public class AgoldUsbTest {
    @Test public void bridgedOnlyWithTheHalProperty() {
        assertTrue(AgoldUsb.bridged("yes\n"));                     // Atom XL: ro.agold.extmodule.hal=yes
        assertFalse(AgoldUsb.bridged(""));                         // Armor 26 Ultra, W6: no such property
        assertFalse(AgoldUsb.bridged("no"));
        assertFalse(AgoldUsb.bridged(null));
    }

    @Test public void powerStateFromTheBridgeDriver() {
        assertEquals(Boolean.TRUE, AgoldUsb.powered("power on = 1\n"));     // as the Atom XL's mcu driver prints it
        assertEquals(Boolean.FALSE, AgoldUsb.powered("power on = 0"));
        assertNull(AgoldUsb.powered(""));                                   // node missing: unknown, the port is left alone
        assertNull(AgoldUsb.powered("cat: No such file or directory"));
    }

    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x ", x & 0xFF)); return s.toString().trim(); }

    @Test public void requestsAreTheStockAppsBytes() {
        // IComUtils.makePackage({0,1},{1},{1}) and ({1,52},{1},{1}), as sent on the Atom XL 2026-10-28
        assertEquals("7e 96 69 00 01 01 00 01 01 fd fd 81", hex(AgoldFrames.request(0x00, 0x01, 0x01)));
        assertEquals("7e 96 69 01 34 01 00 01 01 fc ca 81", hex(AgoldFrames.request(0x01, 0x34, 0x01)));
    }

    // the replies the Atom XL's bridge and module gave the probe (and the stock app) on 2026-10-28
    private static final String BRIDGE = " 7e 96 69 00 01 00 00 0e 32 30 32 30 31 30 31 36 31 36 31 35 34 31 8e a1 81";
    private static final String MODULE = " 7e 96 69 01 34 00 00 10 44 4d 52 30 30 36 2e 55 34 41 2e 56 30 36 37 00 18 0d 81";

    @Test public void probeRepliesDecodedAndChecked() {
        String d = AgoldUsb.describe("port: after 10 tenths of a second\nbridge: " + BRIDGE + "\nmodule: " + MODULE + "\nafter: power on = 0, HAL running\n");
        assertTrue(d, d.contains("bridge version (route 00, cmd 0x01): \"20201016161541\", checksum ok"));
        assertTrue(d, d.contains("module version (route 01, cmd 0x34): \"DMR006.U4A.V067\", checksum ok"));
        assertTrue(d, d.contains(AgoldUsb.MODULE_ANSWERED + "DMR006.U4A.V067 (bridge 20201016161541)"));
        assertTrue(d, d.contains("clean-up: radio off, HAL running"));
        assertFalse(d.contains(AgoldUsb.BRIDGE_SILENT));
    }

    @Test public void aWrongChecksumIsNotAnAnswer() {
        String bad = MODULE.replace("18 0d", "18 0e");
        assertNull(AgoldUsb.text(bad, 0x01, 0x34));
        assertTrue(AgoldUsb.describe("module: " + bad + "\n").contains("checksum WRONG"));
    }

    @Test public void aSilentBridgeIsAWarning() {
        String d = AgoldUsb.describe("port: none after 10 s\nafter: power on = 0, HAL running\n");
        assertTrue(d.contains("not asked"));
        assertTrue(d.contains(AgoldUsb.BRIDGE_SILENT));
        assertFalse(d.contains(AgoldUsb.MODULE_ANSWERED));
    }

    @Test public void controllerDirFromTheUdcLink() {
        assertEquals("/sys/devices/platform/11201000.usb3", AgoldUsb.controllerDir("/sys/devices/platform/11201000.usb3/udc/musb-hdrc\n"));
        assertNull(AgoldUsb.controllerDir("/sys/class/udc/*"));     // the glob matched nothing
        assertNull(AgoldUsb.controllerDir(""));
        assertNull(AgoldUsb.controllerDir("readlink: not found"));
    }

    @Test public void releaseNoteForTheSummary() {
        String watch = "start com.agold.intercom/.IComActivity: Starting: Intent { … }\n"
                + FirstRunCheck.RELEASED + "power-off (agui.intercom.intent.action.STOP_DEVICE): Broadcast completed: result=0\n"
                + "  the radio is off after 0.5 s (/sys/bus/platform/drivers/mcu/mcu_power = 0)\n"
                + "  USB port given back to the socket (/sys/devices/platform/11201000.usb3/cmode 0, then 1)\n"
                + "closed: stopped\n";
        String n = FirstRunCheck.releaseNote(watch);
        assertTrue(n.startsWith("power-off (agui.intercom.intent.action.STOP_DEVICE)"));
        assertTrue(n.endsWith("cmode 0, then 1)"));
        assertFalse(n.contains("closed:"));
        assertNull(FirstRunCheck.releaseNote("start x: ok\nclosed: stopped\n"));   // any phone without the bridge
        assertNull(FirstRunCheck.releaseNote(null));
    }
}
