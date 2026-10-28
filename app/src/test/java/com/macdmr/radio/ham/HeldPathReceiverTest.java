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
import java.util.Random;

/**
 * The live receiver on a synthetic held-path transmission (clean M17 baseband through the receiver's own pulse,
 * identity equaliser): a call longer than the 20 s buffer stays one call and is payload-exact; a short dropout is
 * bridged; frames at the edge of the audio received so far are never given out half-equalised (R-I, 2026-10-25).
 */
public class HeldPathReceiverTest {
    private static final int FRAMES_LONG = 760;     // 30.4 s of voice frames: past the 20 s buffer cap

    /** Identity equaliser: the synthetic signal has no phone path to undo. */
    static double[] identity() { double[] t = new double[HeldPath.TAPS]; t[HeldPath.L] = 1; return t; }

    /** A transmission as the sender builds it, as 8 kHz audio of the sign the receiver expects. Payload of frame k = payload(seed, k). */
    static short[] transmission(int frames, long seed, byte[] lsf) {
        List<int[]> parts = new ArrayList<>();
        parts.add(M17.preamble()); parts.add(M17.lsfSymbols(lsf));
        for (int k = 0; k < frames; k++) parts.add(M17.streamSymbols(lsf, k | (k == frames - 1 ? 0x8000 : 0), payload(seed, k), k % 6));
        parts.add(M17.eotTail());
        int total = 0; for (int[] p : parts) total += p.length;
        int[] sym = new int[total]; int at = 0; for (int[] p : parts) { System.arraycopy(p, 0, sym, at, p.length); at += p.length; }
        return shape(sym);
    }

    static byte[] payload(long seed, int k) { byte[] p = new byte[16]; new Random(seed * 7919 + k).nextBytes(p); return p; }

    /** Symbols at 4800/s to 8 kHz audio: impulses at 48 kHz, the root-raised-cosine pulse, then every sixth sample, scaled and sign-flipped. */
    static short[] shape(int[] sym) {
        int sps = HeldPath.SPS;
        double[] rrc = HeldPath.rrc(0.5, 8, sps);
        int n48 = (sym.length + 16) * sps;
        double[] x = new double[n48];
        for (int k = 0; k < sym.length; k++) {
            int c = (k + 8) * sps;
            for (int j = 0; j < rrc.length; j++) { int i = c + j - rrc.length / 2; if (i >= 0 && i < n48) x[i] += sym[k] * rrc[j]; }
        }
        double peak = 0; for (double v : x) peak = Math.max(peak, Math.abs(v));
        short[] out = new short[n48 / 6];
        for (int i = 0; i < out.length; i++) out[i] = (short) Math.round(-x[i * 6] / peak * 9000);
        return out;
    }

    static short[] silence(int n) { return new short[n]; }
    static short[] concat(short[]... a) { int n = 0; for (short[] x : a) n += x.length; short[] o = new short[n]; int at = 0; for (short[] x : a) { System.arraycopy(x, 0, o, at, x.length); at += x.length; } return o; }

    private static final class Log implements HeldPathReceiver.Listener {
        final List<String> started = new ArrayList<>(), ended = new ArrayList<>();
        final List<M17.Stream> frames = new ArrayList<>();
        final List<byte[]> packets = new ArrayList<>();
        int lost;
        public void started(M17.Lsf l) { started.add(l.src() + ">" + l.dst()); }
        public void link(M17.Lsf l) { started.add("link"); }
        public void frame(M17.Stream s, float q) { frames.add(s); }
        public void ended(int f, int g, String why) { ended.add(why + " " + f); }
        public void packet(M17.Lsf l, byte[] body) { packets.add(body); }
        public void framesLost() { lost++; }
    }

    /** Preamble, packet link setup, the packet frames and the end marker, as the receiver's audio. */
    static short[] packetAudio(byte[] data) {
        byte[] lsf = M17.encodePacketLsf("N0CALL", "@ALL", 0);
        List<int[]> parts = new ArrayList<>();
        parts.add(M17.preamble());
        parts.add(M17.lsfSymbols(lsf));
        parts.addAll(M17.encodePacket(data));
        parts.add(M17.eotTail());
        int total = 0; for (int[] p : parts) total += p.length;
        int[] sym = new int[total]; int at = 0;
        for (int[] p : parts) { System.arraycopy(p, 0, sym, at, p.length); at += p.length; }
        return shape(sym);
    }

    @Test public void aDamagedPacketNotesFramesLost() throws Exception {
        byte[] good = M17.smsPayload("hi");
        byte[] bad = good.clone();
        bad[bad.length - 1] ^= (byte) 0xFF;
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, concat(silence(4000), packetAudio(good), silence(8000), packetAudio(bad), silence(12000)), 4);
        for (int w = 0; log.ended.size() < 2 && w < 4; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("good text", "hi", M17.smsText(log.packets.get(0)));
        assertEquals("only the good packet", 1, log.packets.size());
        assertEquals("one frames-lost note", 1, log.lost);
    }

    /** Feeds audio in 512-sample blocks with a receiver pass every {@code every} blocks, the way the thread polls while audio arrives. */
    static void run(HeldPathReceiver rx, short[] audio, int every) throws Exception {
        byte[] block = new byte[1024]; int blocks = 0;
        for (int off = 0; off < audio.length; off += 512) {
            int n = Math.min(512, audio.length - off);
            for (int k = 0; k < n; k++) { block[2 * k] = (byte) audio[off + k]; block[2 * k + 1] = (byte) (audio[off + k] >> 8); }
            rx.feed(block, n * 2);
            if (++blocks % every == 0) rx.poll();
        }
        rx.poll();
    }

    @Test public void aCallLongerThanTheBufferStaysOneCallAndIsPayloadExact() throws Exception {
        byte[] lsf = M17.lsf("N0CALL", "@ALL", 0x0005, null);
        short[] audio = concat(silence(4000), transmission(FRAMES_LONG, 1, lsf), silence(12000));
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, audio, 4);
        for (int w = 0; log.ended.isEmpty() && w < 3; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("one start: " + log.started, 1, log.started.size());
        assertEquals("N0CALL>@ALL", log.started.get(0));
        assertEquals("one end: " + log.ended, 1, log.ended.size());
        assertTrue(log.ended.get(0), log.ended.get(0).startsWith("last frame"));
        assertTrue("frames " + log.frames.size(), log.frames.size() >= FRAMES_LONG - 2);
        int wrong = 0, prev = -1;
        for (M17.Stream s : log.frames) {
            int k = s.fn & 0x7FFF;
            assertTrue("frame numbers climb: " + prev + " then " + k, k > prev); prev = k;
            if (!java.util.Arrays.equals(payload(1, k), s.payload)) wrong++;
        }
        assertEquals("payload-exact, no half-equalised edge frames", 0, wrong);
        assertTrue(log.frames.get(log.frames.size() - 1).last());
    }

    @Test public void aShortDropoutDoesNotEndTheCall() throws Exception {
        byte[] lsf = M17.lsf("W9XYZ", "N0CALL", 0x0005, null);
        short[] tx = transmission(300, 2, lsf);                           // 12 s
        int hole = 4000 + 320 * 150, holeLen = 320 * 3;                  // three frames of nothing in the middle
        short[] audio = concat(silence(4000), tx, silence(12000));
        for (int i = hole; i < hole + holeLen; i++) audio[i] = 0;
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, audio, 4);
        for (int w = 0; log.ended.isEmpty() && w < 3; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("one start: " + log.started, 1, log.started.size());
        assertEquals("one end: " + log.ended, 1, log.ended.size());
        assertTrue("frames " + log.frames.size(), log.frames.size() >= 300 - 8);
        int wrong = 0; for (M17.Stream s : log.frames) if (!java.util.Arrays.equals(payload(2, s.fn & 0x7FFF), s.payload)) wrong++;
        assertTrue("wrong payloads " + wrong, wrong <= 3);
    }

    @Test public void oneSecondOfSilenceInTheMiddleStaysOneCall() throws Exception {
        byte[] lsf = M17.lsf("N0CALL", "@ALL", 0x0005, null);
        short[] tx = transmission(200, 6, lsf);
        int hole = 4000 + 320 * 80, holeLen = 8000;            // one second, longer than three frames and shorter than the two-second end
        short[] audio = concat(silence(4000), tx, silence(20000));
        for (int i = hole; i < hole + holeLen && i < audio.length; i++) audio[i] = 0;
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, audio, 4);
        for (int w = 0; log.ended.isEmpty() && w < 4; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("one start: " + log.started, 1, log.started.size());
        assertEquals("one end: " + log.ended, 1, log.ended.size());
        assertTrue(log.ended.get(0), log.ended.get(0).startsWith("last frame"));
    }

    @Test public void pollingAfterEveryBlockLosesNothing() throws Exception {
        byte[] lsf = M17.lsf("N0CALL", "@ALL", 0x0005, null);
        short[] audio = concat(silence(4000), transmission(100, 5, lsf), silence(12000));
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, audio, 1);                                                  // a pass after every 64 ms block, more often than the thread ever polls
        for (int w = 0; log.ended.isEmpty() && w < 3; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("one start: " + log.started, 1, log.started.size());
        assertTrue("frames " + log.frames.size(), log.frames.size() >= 98);
        int wrong = 0; for (M17.Stream s : log.frames) if (!java.util.Arrays.equals(payload(5, s.fn & 0x7FFF), s.payload)) wrong++;
        assertEquals(0, wrong);
    }

    @Test public void twoCallsBackToBackAreTwoCalls() throws Exception {
        byte[] a = M17.lsf("N0CALL", "@ALL", 0x0005, null), b = M17.lsf("W9XYZ", "@ALL", 0x0005, null);
        short[] audio = concat(silence(4000), transmission(60, 3, a), silence(6000), transmission(60, 4, b), silence(12000));
        Log log = new Log();
        HeldPathReceiver rx = new HeldPathReceiver(identity(), log, false);
        run(rx, audio, 4);
        for (int w = 0; log.ended.size() < 2 && w < 3; w++) { Thread.sleep(1100); rx.poll(); }
        assertEquals("starts " + log.started, 2, log.started.size());
        assertEquals("N0CALL>@ALL", log.started.get(0)); assertEquals("W9XYZ>@ALL", log.started.get(1));
        assertEquals("ends " + log.ended, 2, log.ended.size());
        assertTrue("frames " + log.frames.size(), log.frames.size() >= 118);
    }
}
