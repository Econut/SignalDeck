/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The PTT capture's views of getevent, on the W6's real output (2026-10-24/27). */
public class PttCaptureTest {
    static final String PL = "add device 1: /dev/input/event14\n  name:     \"sprdphone Headset Keyboard\"\n  events:\n"
            + "    KEY (0001): KEY_VOLUMEDOWN        KEY_VOLUMEUP          KEY_MEDIA             BTN_3                \n"
            + "                BTN_4                \n  input props:\n    <none>\n"
            + "add device 2: /dev/input/event13\n  name:     \"sprdphone Headset Jack\"\n  events:\n"
            + "    SW  (0005): SW_HEADPHONE_INSERT   SW_MICROPHONE_INSERT \n  input props:\n    <none>\n"
            + "add device 3: /dev/input/event12\n  name:     \"hook_dmr\"\n  events:\n    KEY (0001): KEY_F2                KEY_F3               \n  input props:\n    <none>\n"
            + "add device 5: /dev/input/event9\n  name:     \"accelerometer\"\n  events:\n"
            + "    ABS (0003): ABS_X                 : value 0, min -32768, max 32768, fuzz 0, flat 0, resolution 0\n  input props:\n    <none>\n";

    @Test public void devicesWithKeys() {
        String d = DeviceReport.keyDevices(PL);
        assertTrue(d, d.contains("  event12 hook_dmr: KEY_F2 KEY_F3\n"));
        assertTrue(d, d.contains("  event14 sprdphone Headset Keyboard: KEY_VOLUMEDOWN KEY_VOLUMEUP KEY_MEDIA BTN_3 BTN_4\n"));
        assertFalse(d, d.contains("Headset Jack") || d.contains("accelerometer"));         // switches and sensors have no keys
        assertEquals("  (none)\n", DeviceReport.keyDevices(""));
    }

    @Test public void sensorFloodIsCountedNotListed() {
        StringBuilder raw = new StringBuilder("add device 7: /dev/input/event9\n  name:     \"accelerometer\"\nadd device 3: /dev/input/event12\n  name:     \"hook_dmr\"\n");
        for (int i = 0; i < 900; i++) raw.append("[    1423.770433] /dev/input/event9: EV_ABS       ABS_X                ffffff27\n");
        raw.append("[    1425.100000] /dev/input/event12: EV_KEY       KEY_F2               DOWN\n");
        raw.append("[    1425.400000] /dev/input/event12: EV_KEY       KEY_F2               UP\n");
        String e = DeviceReport.keyEvents(raw.toString());
        assertTrue(e, e.contains("/dev/input/event12: EV_KEY       KEY_F2               DOWN   (hook_dmr)"));
        assertTrue(e, e.contains("(900 motion/sensor events from /dev/input/event9 accelerometer left out)"));
        assertFalse(e, e.contains("ABS_X"));
        assertEquals("  no key or switch events\n", DeviceReport.keyEvents(""));
    }
}
