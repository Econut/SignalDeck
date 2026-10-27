/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Set;

/** The probe's port choice from other evidence, and the SA818-class AT answer. Inputs in the shape the W6's report printed. */
public class RadioProbeAtTest {
    @Test public void portsNamedByTheStockApp() {
        String report = "==== Clues in com.flyscale.analogpoc's code (paths, audio parameters, serial speeds, PTT log texts) ====\n"
                + "  paths: /dev/ttyS2  /proc/self/fd/  /proc/tty/drivers  /sys/class/gpio/gpio179/value\n"
                + "  serial speeds: -\n"
                + "/dev/ttySE0: speed 115200 baud; rows 0; columns 0; line = 0;\n";       // a port only listed, not named by the app
        String watch = "start com.flyscale.analogpoc/.main.ChannelActivity: Starting: Intent { … }\n"
                + "devices it holds open: /dev/ashmem6a7a2ce1 /dev/binderfs /dev/ion /dev/null /dev/ttyS2 /dev/urandom\n"
                + "  /dev/ttyS2: speed 9600 baud; rows 0;\n";
        assertEquals(Set.of("/dev/ttyS2"), RadioProbe.namedPorts(report, watch, null));
        assertEquals(Set.of("/dev/ttyS2", "/dev/ttyS1"), RadioProbe.namedPorts(report, "", "/dev/ttyS1"));
        assertTrue(RadioProbe.namedPorts("", "", null).isEmpty());                 // no evidence: the probe falls back to every port
        assertTrue(RadioProbe.namedPorts(null, null, "").isEmpty());
    }

    @Test public void atAnswer() {
        String hex = "2b 44 4d 4f 43 4f 4e 4e 45 43 54 3a 30 0d 0a";                    // "+DMOCONNECT:0\r\n"
        assertTrue(RadioProbe.looksLikeAt(hex));
        assertEquals("+DMOCONNECT:0", RadioProbe.asText(hex));
        String fail = "46 41 49 4c 3a 20 49 6e 76 61 6c 69 64 20 63 6f 6d 6d 61 6e 64 0a"; // ttySE0's "FAIL: Invalid command"
        assertFalse(RadioProbe.looksLikeAt(fail));
        // the W6's module on 2026-10-27: an error echo of the handshake (one byte lost), then a clean version answer
        assertFalse(RadioProbe.looksLikeAt("0d 0a 0a 41 54 2b 44 4d 4f 43 4f 4e 4e 45 43 3a 31 0d 0a"));   // "AT+DMOCONNEC:1"
        assertTrue(RadioProbe.looksLikeAt("2b 44 4d 4f 56 45 52 51 3a 50 42 32 30 32 45 55 30 35 0d 0a"));  // "+DMOVERQ:PB202EU05"
        assertEquals("+DMOVERQ:PB202EU05", RadioProbe.asText("2b 44 4d 4f 56 45 52 51 3a 50 42 32 30 32 45 55 30 35 0d 0a"));
        assertFalse(RadioProbe.looksLikeAt("41 54 2b 44 4d 4f 56 45 52 51 0d 0a"));                          // a loopback: "AT+DMOVERQ"
        assertFalse(RadioProbe.looksLikeModule(fail));
        assertEquals("FAIL: Invalid command", RadioProbe.asText(fail));
        assertEquals("AT+DMOCONNECT\r", new String(RadioProbe.AT_CONNECT));      // a bare \r, as the stock app sends it
        assertEquals("AT+DMOVERQ\r", new String(RadioProbe.AT_VERSION));
    }

    @Test public void anEchoOfTheQueryIsNotAnAnswer() {
        // what the W6's AT module sent back to the DMR version query at 9600 baud (2026-10-27): our frame, wrapped in ":1"
        String echo = "0d 0a 8e 68 34 01 01 95 b9 00 01 01 10 00 00 3a 31 0d 0a";
        assertFalse(RadioProbe.looksLikeModule(echo));
        assertFalse(RadioProbe.looksLikeModule("7e 96 69 01 34 01 00 00 fd cb 81"));               // an Agold query looped back
        assertTrue(RadioProbe.looksLikeModule("68 34 01 01 95 b9 00 01 01 10 68 34 00 0c 12 34")); // echo, then a real answer
        assertTrue(RadioProbe.looksLikeModule("68 34 00 0c 44 4d 52"));
    }

    @Test public void summaryNamesWhereTheModuleAnswered() {
        String probe = "  9600 baud, after AT+DMOCONNECT: 2b 44 4d 4f …\n  ^ " + RadioProbe.AT_MARK + " on /dev/ttyS2 at 9600 baud\n";
        assertEquals(" on /dev/ttyS2 at 9600 baud", FirstRunCheck.atWhere(probe));
        assertEquals("", FirstRunCheck.atWhere("  ^ looks like the DMR module answered at 57600 baud\n"));
    }
}
