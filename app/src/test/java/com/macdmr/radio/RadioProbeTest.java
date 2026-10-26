/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class RadioProbeTest {
    @Test public void agoldQueriesAreWellFormed() {
        assertArrayEquals(AgoldFramesTest.frame(1, 0x34, 1, new byte[0]), RadioProbe.QUERY_AGOLD_34);
        assertArrayEquals(AgoldFramesTest.frame(1, 0x25, 1, new byte[0]), RadioProbe.QUERY_AGOLD_25);
    }

    @Test public void speedIsReadFromSttyFirstLine() {
        assertEquals("921600", RadioProbe.speedOf("speed 921600 baud; line = 0;"));
        assertEquals("57600", RadioProbe.speedOf("speed 57600 baud; rows 0; columns 0; line = 0;"));
        assertEquals("", RadioProbe.speedOf("stty: /dev/ttyS9: No such file or directory"));
    }

    @Test public void moduleAnswersAreRecognised() {
        assertTrue(RadioProbe.looksLikeModule("68 34 01 00 ..."));
        assertTrue(RadioProbe.looksLikeModule("7E 96 69 01 34 00 00 0F"));
        assertFalse(RadioProbe.looksLikeModule("00 ff 12"));
    }
}
