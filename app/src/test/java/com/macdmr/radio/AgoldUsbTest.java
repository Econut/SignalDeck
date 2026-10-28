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
