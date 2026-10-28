/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Packet mode from the current M17 specification: frame split, CRC, and a soft decode with two bad symbols. */
public class M17PacketTest {
    @Test public void roundTripAtTheChunkBoundaries() {
        for (int n : new int[] { 1, 25, 26, 200 }) {
            byte[] data = new byte[n];
            for (int i = 0; i < n; i++) data[i] = (byte) (i * 17 + 3);
            int c = M17.crc16(data, n);
            byte[] with = new byte[n + 2];
            System.arraycopy(data, 0, with, 0, n);
            with[n] = (byte) (c >> 8); with[n + 1] = (byte) c;
            List<int[]> frames = M17.encodePacket(with);
            assertEquals((n + 2 + 24) / 25, frames.size());
            List<M17.Packet> got = new ArrayList<>();
            int fn = 0;
            for (int i = 0; i < frames.size(); i++) {
                M17.Packet p = M17.decodePacket(M17Test.toFloat(frames.get(i)), 8);
                boolean last = i == frames.size() - 1;
                assertEquals(last, p.eof);
                if (last) assertEquals(with.length - i * 25, p.counter);
                else { assertEquals(fn, p.counter); fn++; }
                got.add(p);
            }
            assertArrayEquals(data, java.util.Arrays.copyOf(M17.assemble(got), n));
        }
    }

    @Test public void smsCrcAndLastFrameCount() {
        byte[] sms = M17.smsPayload("hi");
        assertEquals(0x05, sms[0] & 0xFF);
        assertEquals(0, sms[3]);
        int c = M17.crc16(sms, sms.length - 2);
        assertEquals(c, ((sms[sms.length - 2] & 0xFF) << 8) | (sms[sms.length - 1] & 0xFF));
        List<int[]> frames = M17.encodePacket(sms);
        assertEquals(1, frames.size());
        M17.Packet p = M17.decodePacket(M17Test.toFloat(frames.get(0)), 8);
        assertTrue(p.eof);
        assertEquals(sms.length, p.count());
        byte[] body = M17.assemble(java.util.Collections.singletonList(p));
        assertEquals("hi", M17.smsText(body));
        byte[] lsf = M17.encodePacketLsf("N0CALL", "@ALL", 3);
        M17.Lsf l = M17.decodeLsf(M17Test.toFloat(M17.lsfSymbols(lsf)), 8);
        assertTrue(l.crcOk);
        assertFalse(l.stream());
        assertEquals(3, l.can());
        assertEquals("N0CALL", l.src());
        assertEquals("@ALL", l.dst());
    }

    @Test public void twoSymbolErrorsStillDecode() {
        byte[] data = M17.smsPayload("packet");
        int[] sym = M17.encodePacket(data).get(0);
        sym[20] = -sym[20];
        sym[40] = -sym[40];
        M17.Packet p = M17.decodePacket(M17Test.toFloat(sym), 8);
        assertEquals("packet", M17.smsText(M17.assemble(java.util.Collections.singletonList(p))));
    }
}
