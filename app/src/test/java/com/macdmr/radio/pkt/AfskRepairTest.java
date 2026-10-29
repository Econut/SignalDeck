/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

/** The one-bit repair still fixes a frame with one wrong bit after it was moved behind the slicers and made cheaper. */
public class AfskRepairTest {
    private static List<byte[]> decodeWithOneBitWrong(byte[] frame, boolean fix) {
        byte[] f = Ax25.appendFcs(frame);
        f[f.length - 6] ^= 0x10;                                             // one bit of the text, the FCS left as it was
        short[] audio = AFSKGenerator.generateAFSKWithNRZI(f, 30);
        short[] padded = new short[audio.length + 4000]; System.arraycopy(audio, 0, padded, 2000, audio.length);
        AFSKDecoder.BIT_FIX = fix;
        try { return AFSKDecoder.decode(AfskBenchTest.up6(padded)); } finally { AFSKDecoder.BIT_FIX = true; }
    }

    @Test public void aFrameWithOneWrongBitIsRepaired() {
        byte[] ui = Ax25.buildUiFrame("APRS", 0, "N0CALL", 7, new String[] { "WIDE1-1" }, "!4000.00N/10500.00W-bench repair test".getBytes());
        byte[] frame = java.util.Arrays.copyOf(ui, ui.length - 2);          // without its FCS: the decoder returns frames without one
        assertTrue("without the repair the checksum fails", decodeWithOneBitWrong(frame, false).isEmpty());
        int before = AFSKDecoder.fixedFrames;
        List<byte[]> got = decodeWithOneBitWrong(frame, true);
        assertEquals(1, got.size());
        assertArrayEquals("the wrong bit is put right", frame, got.get(0));
        assertTrue(AFSKDecoder.fixedFrames > before);
    }
}
