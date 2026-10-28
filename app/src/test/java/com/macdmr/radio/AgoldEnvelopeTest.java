/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Host checks of the Agold envelope against bytes captured on the Atom XL. No radio. */
public class AgoldEnvelopeTest {
    private static byte[] hex(String s) {
        String[] p = s.trim().split(" +");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    @Test public void versionRequestMatchesTheCapturedFrame() {
        byte[] got = new AgoldEnvelope().encode(Frame.request(0x34, (byte) 0x01));
        assertArrayEquals(hex("7e 96 69 01 34 01 00 01 01 fc ca 81"), got);
        assertArrayEquals(AgoldFrames.request(0x01, 0x34, 0x01), got);
    }

    @Test public void versionReplyKeepsTheBody() {
        byte[] raw = hex("7e 96 69 01 34 00 00 10 44 4d 52 30 30 36 2e 55 34 41 2e 56 30 36 37 00 18 0d 81");
        Frame[] out = new Frame[1];
        assertEquals(raw.length, new AgoldEnvelope().tryDecode(raw, 0, raw.length, out));
        assertEquals(0x34, out[0].cmd);
        assertEquals(0, out[0].sr);
        assertArrayEquals("DMR006.U4A.V067\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), out[0].body);
    }

    @Test public void setReplyPutsTheResultInSr() {
        AgoldEnvelope env = new AgoldEnvelope();
        Frame[] out = new Frame[1];
        byte[] ok = hex("7e 96 69 01 22 00 00 01 00 fd dd 81");
        assertEquals(ok.length, env.tryDecode(ok, 0, ok.length, out));
        assertEquals(0x22, out[0].cmd);
        assertEquals(0, out[0].sr);
        assertEquals(0, out[0].body.length);
        byte[] refused = hex("7e 96 69 01 22 00 00 01 01 fd dc 81");
        assertEquals(refused.length, env.tryDecode(refused, 0, refused.length, out));
        assertEquals(1, out[0].sr);
        assertEquals(0, out[0].body.length);
        byte[] vol = hex("7e 96 69 01 2e 00 00 01 00 fd d1 81");
        assertEquals(vol.length, env.tryDecode(vol, 0, vol.length, out));
        assertEquals(0x2E, out[0].cmd);
        assertEquals(0, out[0].sr);
    }

    @Test public void bridgeReportDoesNotBecomeAModuleFrame() {
        byte[] full = hex("7e 96 69 00 01 00 00 0e 32 30 32 30 31 30 31 36 31 36 31 35 34 31 8e a1 81");
        List<int[]> heard = new ArrayList<>();
        AgoldEnvelope env = new AgoldEnvelope();
        env.setBridge((cmd, b) -> heard.add(new int[] { cmd, b.length }));
        Frame[] out = new Frame[1];
        assertEquals(full.length, env.tryDecode(full, 0, full.length, out));
        assertNull(out[0]);
        assertEquals(1, heard.size());
        assertEquals(0x01, heard.get(0)[0]);
        assertEquals(14, heard.get(0)[1]);
    }

    @Test public void aBadChecksumIsNotAFrameAndACutFrameWaits() {
        byte[] ok = hex("7e 96 69 01 22 00 00 01 00 fd dd 81");
        byte[] bad = ok.clone();
        bad[9] ^= 0x01;
        Frame[] out = new Frame[1];
        assertEquals(-1, new AgoldEnvelope().tryDecode(bad, 0, bad.length, out));
        assertEquals(0, new AgoldEnvelope().tryDecode(ok, 0, ok.length - 3, out));
    }

    @Test public void garbageBeforeAFrameIsSkipped() {
        byte[] frame = hex("7e 96 69 01 2e 00 00 01 00 fd d1 81");
        byte[] buf = new byte[frame.length + 3];
        buf[0] = 0x00; buf[1] = 0x11; buf[2] = 0x22;
        System.arraycopy(frame, 0, buf, 3, frame.length);
        AgoldEnvelope env = new AgoldEnvelope();
        Frame[] out = new Frame[1];
        int off = 0;
        Frame got = null;
        while (off < buf.length) {
            int used = env.tryDecode(buf, off, buf.length - off, out);
            if (used > 0) { if (out[0] != null) { got = out[0]; break; } off += used; continue; }   // zero padding is consumed without a frame
            if (used == 0) fail("asked for more bytes on a complete buffer");
            off++;
        }
        assertNotNull(got);
        assertEquals(0x2E, got.cmd);
        assertEquals(0, got.sr);
    }

    @Test public void statusPushKeepsItsBodyAndTheReplyFoldsTheResult() {
        AgoldEnvelope env = new AgoldEnvelope();
        Frame[] out = new Frame[1];
        byte[] push = AgoldFramesTest.frame(1, 0x36, 1, new byte[] { 3 });
        assertEquals(push.length, env.tryDecode(push, 0, push.length, out));
        assertEquals(0x36, out[0].cmd);
        assertEquals(0, out[0].sr);
        assertArrayEquals(new byte[] { 3 }, out[0].body);
        byte[] reply = AgoldFramesTest.frame(1, 0x36, 0, new byte[] { 0 });
        assertEquals(reply.length, env.tryDecode(reply, 0, reply.length, out));
        assertEquals(0, out[0].sr);
        assertEquals(0, out[0].body.length);
        byte[] refused = AgoldFramesTest.frame(1, 0x36, 0, new byte[] { 1 });
        env.tryDecode(refused, 0, refused.length, out);
        assertEquals(1, out[0].sr);
        assertEquals(0, out[0].body.length);
    }

    @Test public void unlistedRepliesAreMappedAndLoggedOnce() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        AgoldEnvelope env = new AgoldEnvelope();
        env.setLog(lines::add);
        Frame[] out = new Frame[1];
        byte[] result = AgoldFramesTest.frame(1, 0x26, 0, new byte[] { 1 });
        assertEquals(result.length, env.tryDecode(result, 0, result.length, out));
        assertEquals(0x26, out[0].cmd);
        assertEquals(1, out[0].sr);
        assertEquals(0, out[0].body.length);
        byte[] again = AgoldFramesTest.frame(1, 0x26, 0, new byte[] { 0 });
        env.tryDecode(again, 0, again.length, out);
        byte[] data = AgoldFramesTest.frame(1, 0x3B, 0, new byte[] { 4, 5 });
        env.tryDecode(data, 0, data.length, out);
        assertEquals(0, out[0].sr);
        assertArrayEquals(new byte[] { 4, 5 }, out[0].body);
        assertEquals(java.util.Arrays.asList("envelope cmd=0x26 unlisted", "envelope cmd=0x3B unlisted"), lines);
    }

    @Test public void routeTwoAudioReportsAreLoggedNotDelivered() {
        // captured on the Atom XL 2026-10-28: the bridge's audio stream opened, then closed (as padded 20-byte reads)
        java.util.List<String> lines = new java.util.ArrayList<>();
        AgoldEnvelope env = new AgoldEnvelope();
        env.setLog(lines::add);
        Frame[] out = new Frame[1];
        byte[] on = hex("7e 96 69 02 67 00 00 01 01 fc 97 81"), off = hex("7e 96 69 02 66 00 00 01 01 fc 98 81");
        assertEquals(on.length, env.tryDecode(on, 0, on.length, out));
        assertNull(out[0]);
        assertEquals(off.length, env.tryDecode(off, 0, off.length, out));
        assertNull(out[0]);
        env.tryDecode(on, 0, on.length, out);
        assertEquals(java.util.Arrays.asList("envelope route 02 cmd=0x67 len=1 (audio stream opened)", "envelope route 02 cmd=0x66 len=1 (audio stream closed)"), lines);
    }

    @Test public void zeroPaddingIsSkippedSilently() {
        // a 12-byte RSSI reply padded to the bridge's 20-byte packet, then the next frame
        byte[] rssi = hex("7e 96 69 01 32 00 00 01 1b fd b2 81"), pad = new byte[8];
        byte[] all = new byte[rssi.length + pad.length + rssi.length];
        System.arraycopy(rssi, 0, all, 0, rssi.length); System.arraycopy(rssi, 0, all, rssi.length + pad.length, rssi.length);
        AgoldEnvelope env = new AgoldEnvelope();
        Frame[] out = new Frame[1];
        int off = env.tryDecode(all, 0, all.length, out);
        assertEquals(12, off);
        assertEquals(0x32, out[0].cmd);
        assertEquals(8, env.tryDecode(all, off, all.length - off, out));
        assertNull(out[0]);
        assertEquals(12, env.tryDecode(all, 20, all.length - 20, out));
        assertEquals(0x1B, out[0].body[0] & 0xFF);
    }

    @Test public void armorCodecMatchesFrame() {
        FrameCodec codec = FrameCodec.armor();
        Frame f = Frame.request(0x23, (byte) 1, (byte) 2);
        assertArrayEquals(f.encode(), codec.encode(f));
        byte[] raw = f.encode();
        Frame[] out = new Frame[1];
        assertEquals(raw.length, codec.tryDecode(raw, 0, raw.length, out));
        assertEquals(0x23, out[0].cmd);
        assertEquals(1, out[0].body[0] & 0xFF);
        assertEquals(-1, codec.tryDecode(new byte[] { 0x00, 0x68 }, 0, 2, out));
    }
}
