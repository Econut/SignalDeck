/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RecordingDecoderTest {
    private static byte[] res(String path) throws Exception { try (InputStream in = RecordingDecoderTest.class.getResourceAsStream(path)) { return in.readAllBytes(); } }

    @Test public void sstvPictureFromARecording() throws Exception {
        short[] s = RecordingDecoder.read8k(res("/sstv/r36_ref.wav"));
        final List<long[]> pics = new ArrayList<>();
        int n = new RecordingDecoder().sstv(s, new RecordingDecoder.Listener() {
            @Override public void picture(int[] argb, int w, int h, int lines, boolean complete, long atMs) { pics.add(new long[] { w, h, lines, complete ? 1 : 0, atMs }); }
        });
        assertEquals(1, n); assertEquals(1, pics.size());
        long[] p = pics.get(0); assertEquals(320, p[0]); assertEquals(240, p[1]); assertTrue("lines " + p[2], p[2] >= 230);
        assertTrue("ends inside the recording", p[4] > 30_000 && p[4] <= s.length * 1000L / 8000);
    }
    @Test public void severalPicturesInOnePassRecording() throws Exception {      // the ISS events: a picture, about 2 minutes off, the next one
        short[] pic = RecordingDecoder.read8k(res("/sstv/r36_ref.wav"));
        java.util.Random rnd = new java.util.Random(3); int gap = 8000 * 120;
        short[] s = new short[pic.length * 3 + gap * 3];
        int o = 0;
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < gap; i++) s[o++] = (short) (rnd.nextGaussian() * 3000);              // receiver noise between pictures
            for (int i = 0; i < pic.length; i++) s[o++] = (short) Math.max(-32768, Math.min(32767, pic[i] + rnd.nextGaussian() * 600));
        }
        final List<long[]> pics = new ArrayList<>();
        int n = new RecordingDecoder().sstv(s, new RecordingDecoder.Listener() {
            @Override public void picture(int[] argb, int w, int h, int lines, boolean complete, long atMs) { pics.add(new long[] { lines, complete ? 1 : 0, atMs }); }
        });
        StringBuilder d = new StringBuilder(); for (long[] p : pics) d.append(java.util.Arrays.toString(p)).append(' ');
        assertEquals("pictures: " + d, 3, n);
        for (int k = 0; k < 3; k++) {
            assertTrue("picture " + k + " complete: " + d, pics.get(k)[1] == 1);
            long endMs = (long) (k + 1) * (gap + pic.length) * 1000 / 8000;                          // where picture k ends in the recording
            assertTrue("picture " + k + " ends near " + endMs + ": " + d, Math.abs(pics.get(k)[2] - endMs) < 8000);
        }
    }
    @Test public void aprsPacketsFromARecording() throws Exception {
        short[] s = RecordingDecoder.read8k(res("/aprs/noise100_flat.wav"));
        final List<Long> at = new ArrayList<>();
        int n = new RecordingDecoder().aprs(s, new RecordingDecoder.Listener() { @Override public void packet(byte[] f, long ms) { at.add(ms); } });
        assertTrue("decoded " + n + " (direwolf: 24)", n >= 24); assertEquals(n, at.size());
        for (int i = 1; i < at.size(); i++) assertTrue(at.get(i) >= at.get(i - 1));
    }
    @Test public void stereoAndOtherRatesAreConverted() throws Exception {
        int rate = 16000, frames = 1600; byte[] w = new byte[44 + frames * 4];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(w).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + frames * 4).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4).putShort((short) 4).putShort((short) 16).put("data".getBytes()).putInt(frames * 4);
        for (int i = 0; i < frames; i++) b.putShort((short) 1000).putShort((short) 3000);
        short[] s = RecordingDecoder.read8k(w);
        assertEquals(800, s.length); assertEquals(2000, s[100]);
        try { RecordingDecoder.read8k(new byte[100]); fail(); } catch (java.io.IOException expected) { }
    }
    @Test public void cancelStopsEarly() throws Exception {
        short[] s = RecordingDecoder.read8k(res("/sstv/r36_ref.wav"));
        final RecordingDecoder d = new RecordingDecoder();
        int n = d.sstv(s, new RecordingDecoder.Listener() { @Override public void progress(int pct) { if (pct >= 10) d.cancel(); } });
        assertEquals(0, n);
    }
    @Test public void startTimeFromTheFileName() {
        assertTrue(RecordingDecoder.startFromName("20261023_040312_pass.wav") > 0);
        assertEquals(-1, RecordingDecoder.startFromName("noise.wav"));
        assertEquals("12:05", RecordingDecoder.clock(725_400));
    }
}
