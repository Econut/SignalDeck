/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The serial sniff's decoded view. The W6 trace is what sdtrace printed on the W6 on 2026-10-27 (the stock app's start-up). */
public class SerialTraceTest {
    static final String W6 = "sdtrace: pid 29143, watching /dev/tty for 12000 ms\n"
            + "718.3 sdtrace: attached to 17 threads\n"
            + "1081.2 29143 open /dev/ttyS2 -> fd 68\n"
            + "1081.5 29143 get fd 68 9600 8N1 raw iflag=0 oflag=0x4 cflag=0xcbd lflag=0xa30 -> 0\n"
            + "1081.6 29143 set fd 68 9600 8N1 raw iflag=0 oflag=0x4 cflag=0xcbd lflag=0xa30 -> 0\n"
            + "1173.6 29166 W fd 68 14: 41 54 2b 44 4d 4f 43 4f 4e 4e 45 43 54 0d |AT+DMOCONNECT.|\n"
            + "1387.3 29179 R fd 68 17: 0d 0a 2b 44 4d 4f 43 4f 4e 4e 45 43 54 3a 30 0d 0a |..+DMOCONNECT:0..|\n"
            + "1474.1 29185 W fd 68 11: 41 54 2b 44 4d 4f 56 45 52 51 0d |AT+DMOVERQ.|\n"
            + "1583.5 29183 W fd 68 45: 41 54 2b 44 4d 4f 53 45 54 47 52 4f 55 50 3d 31 2c 34 33 35 2e 30 32 35 2c 34 33 35 2e 30 32 35 2c 32 34 30 2c 32 34 30 2c 33 2c 31 0d |AT+DMOSETGROUP=1,435.025,435.025,240,240,3,1.|\n"
            + "1675.4 29166 W fd 68 18: 41 54 2b 44 4d 4f 53 45 54 56 4f 4c 55 4d 45 3d 31 0d |AT+DMOSETVOLUME=1.|\n"
            + "1682.5 29184 R fd 68 20: 2b 44 4d 4f 56 45 52 51 3a 50 42 32 30 32 45 55 30 35 0d 0a |+DMOVERQ:PB202EU05..|\n"
            + "1832.9 29165 R fd 68 37: 0d 0a 2b 44 4d 4f 53 45 54 47 52 4f 55 50 3a 30 0d 0a 0d 0a 2b 44 4d 4f 53 45 54 56 4f 4c 55 4d 45 3a 30 0d 0a |..+DMOSETGROUP:0....+DMOSETVOLUME:0..|\n"
            + "12773.2 sdtrace: detached; 77 waiting-byte polls (FIONREAD) not listed\n";

    @Test public void w6StartUp() {
        String s = SerialTrace.summary(W6);
        assertTrue(s, s.contains("  port: /dev/ttyS2\n"));
        assertTrue(s, s.contains("  line set by the app: 9600 8N1 raw\n"));
        assertTrue(s, s.contains("  protocol: SA818-class AT commands (4 of 4 writes start AT+DMO)\n"));
        assertTrue(s, s.contains("  traffic: 4 writes (88 bytes), 3 reads (74 bytes)\n"));
        assertTrue(s, s.contains("  commands end with: \\r (one write per command)\n"));
        assertTrue(s, s.contains("     +92 ms  AT+DMOCONNECT  -> +DMOCONNECT:0\n"));
        assertTrue(s, s.contains("    +393 ms  AT+DMOVERQ  -> +DMOVERQ:PB202EU05\n"));
        assertTrue(s, s.contains("AT+DMOSETGROUP=1,435.025,435.025,240,240,3,1  -> +DMOSETGROUP:0\n"));
        assertTrue(s, s.contains("AT+DMOSETVOLUME=1  -> +DMOSETVOLUME:0\n"));
        assertEquals(W6, SerialTrace.redact(W6));                   // AT text has nothing to blank
    }

    @Test public void otherFamiliesAndNothing() {
        String dmr = "10.0 1 open /dev/ttyS1 -> fd 9\n"
                + "20.0 1 W fd 9 10: 68 34 01 01 95 b9 00 01 01 10 |h4........|\n";
        assertTrue(SerialTrace.summary(dmr).contains("protocol: the DMR module's 68 … 10 frames (1 of 1 writes)"));
        assertTrue(SerialTrace.summary("12.0 1 open /dev/ttyS3 -> fd 4\n").contains("protocol: none: the app wrote nothing"));
        assertEquals("  no serial port opened or used while it was watched\n", SerialTrace.summary("sdtrace: pid 1, watching /dev/tty for 15000 ms\n"));
        assertTrue(SerialTrace.summary("1.0 1 W fd 3 3: 01 02 03 |...|\n").contains("protocol: unknown (1 writes"));
    }

    @Test public void radioIdsInDmrFramesAreBlanked() {
        // a channel write carries the phone's own radio ID in its body
        String l = "30.0 2 W fd 9 14: 68 22 01 01 aa bb 00 04 12 d6 87 00 01 10 |h\"..........|\n";
        String r = SerialTrace.redact(l);
        assertTrue(r, r.startsWith("30.0 2 W fd 9 14: 68 22 01 01 aa bb 00 04 xx xx xx xx xx 10 |"));
        assertTrue(r, r.contains("(frame body blanked)"));
        assertFalse(r, r.contains("12 d6 87"));
    }
}
