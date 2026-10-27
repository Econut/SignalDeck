/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import com.macdmr.radio.coverage.Morse;
import com.macdmr.radio.ham.CwDecoder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Item 8: the Morse decoder pauses while packet audio is received (direwolf's APRS benchmark files, at 8 kHz). */
public class CwGateTest {
    private static final long NO_FRAME = 1_000_000_000_000L;   // a wall clock far from any packetHeard()

    /** Feeds 10 ms blocks; returns per block whether the gate was active. */
    private static boolean[] activity(short[] a) {
        AfskPresence p = new AfskPresence(); boolean[] on = new boolean[a.length / 80];
        for (int i = 0; i < on.length; i++) { short[] b = new short[80]; System.arraycopy(a, i * 80, b, 0, 80); p.process(b); on[i] = p.active(NO_FRAME); }
        return on;
    }

    @Test public void everyWindowWithAPacketHasTheGateOn() throws Exception {
        for (String name : new String[] { "flat", "de", "pre" }) {
            short[] a = AfskBenchTest.wav("noise100_" + name + ".wav");
            boolean[] on = activity(a);
            int windows = 0, covered = 0;
            for (int start = 0; start < a.length; start += 16000) {                 // PacketEngine's 2 s windows, 1 s overlap
                int from = Math.max(0, start - 8000), to = Math.min(a.length, start + 16000);
                short[] win = new short[to - from]; System.arraycopy(a, from, win, 0, win.length);
                boolean packet = false; for (byte[] f : AFSKDecoder.decode(AfskBenchTest.up6(win))) if (Ax25.plausible(f)) packet = true;
                if (!packet) continue;
                windows++;
                for (int b = from / 80; b < to / 80 && b < on.length; b++) if (on[b]) { covered++; break; }
            }
            assertTrue(name + ": windows with packets " + windows, windows >= 8);
            assertEquals(name + ": every window the packet decoder hears a packet in has the gate on", windows, covered);
        }
    }

    @Test public void itTurnsOnWithinTheFirstFlags() throws Exception {
        byte[] f = Ax25.buildUiFrame("APDR16", 0, "N0CALL", 7, new String[] { "WIDE1-1" }, "!0000.00N/00000.00E>test".getBytes("US-ASCII"));
        Random r = new Random(5);
        for (int flags : new int[] { 5, 50, 240 })                                 // a short lead-in, the generator's default, SignalDeck's 1.6 s
            for (double noise : new double[] { 0, 5000 }) {
                short[] a = AFSKGenerator.generateAFSKWithNRZI(f, flags); AfskPresence p = new AfskPresence(); int at = -1;
                for (int i = 0; i < a.length && at < 0; i++) { p.process(new short[] { (short) Math.max(-32768, Math.min(32767, a[i] * 0.5 + noise * r.nextGaussian())) }); if (p.active(NO_FRAME)) at = i; }
                assertTrue(flags + " flags, noise " + noise + ": on after " + at + " samples", at >= 0 && at <= 8000 * 50 / 1000);
            }
    }

    @Test public void noiseVoiceAndMorseNeverTurnItOn() {
        Random r = new Random(3);
        for (double lv : new double[] { 300, 3000, 12000 }) {
            short[] x = new short[8000 * 300];
            for (int i = 0; i < x.length; i++) x[i] = (short) Math.max(-32768, Math.min(32767, lv * r.nextGaussian()));
            for (boolean b : activity(x)) assertFalse("noise " + lv, b);
        }
        short[] v = new short[8000 * 300]; double ph = 0;                          // voice-like: a gliding pitch with harmonics, syllable envelope
        for (int i = 0; i < v.length; i++) {
            ph += 2 * Math.PI * (120 + 80 * Math.sin(i / 4000.0)) / 8000; double s = 0;
            for (int h = 1; h < 25; h++) s += Math.sin(h * ph) / h;
            v[i] = (short) (6000 * s * (0.5 + 0.5 * Math.sin(i / 1500.0)) + 200 * r.nextGaussian());
        }
        for (boolean b : activity(v)) assertFalse("voice", b);
        for (double hz : new double[] { 600, 800, 1000 }) {
            short[] m = Morse.pcm("N0CALL N0CALL", 20, hz), x = new short[m.length * 10];
            for (int i = 0; i < x.length; i++) x[i] = m[i % m.length];
            for (boolean b : activity(x)) assertFalse("morse " + hz, b);
        }
    }

    private static List<String> gated(short[] a, long nowMs, boolean strict, java.util.function.Consumer<CwGate> before) {
        List<String> out = new ArrayList<>();
        CwGate g = new CwGate(new CwDecoder((t, hz, wpm) -> out.add(t), strict));
        if (before != null) before.accept(g);
        g.process(a, nowMs);
        g.flush();
        return out;
    }

    private static short[] cat(short[]... parts) {
        int n = 0; for (short[] p : parts) n += p.length;
        short[] o = new short[n]; int k = 0; for (short[] p : parts) { System.arraycopy(p, 0, o, k, p.length); k += p.length; }
        return o;
    }

    @Test public void aMorseIdBeforeAndAfterPacketsIsStillDecoded() throws Exception {
        short[] id = Morse.pcm("N0CALL", 20, 800), quiet = new short[8000 * 3];
        short[] packets = java.util.Arrays.copyOf(AfskBenchTest.wav("noise100_flat.wav"), 8000 * 10);   // the cleanest ten seconds
        List<String> out = gated(cat(id, quiet, packets, quiet, id, quiet), NO_FRAME, true, null);
        assertEquals("[N0CALL, N0CALL]", out.toString());
    }

    @Test public void packetAudioGivesTheDecoderNothing() throws Exception {
        for (String name : new String[] { "flat", "de", "pre" }) {
            short[] a = java.util.Arrays.copyOf(AfskBenchTest.wav("noise100_" + name + ".wav"), 8000 * 20);
            List<String> out = new ArrayList<>(); int fed = 0;
            CwGate g = new CwGate(new CwDecoder((t, hz, wpm) -> out.add(t), false));      // not strict: any text at all would show
            for (int i = 0; i + 80 <= a.length; i += 80) { short[] b = new short[80]; System.arraycopy(a, i, b, 0, 80); g.process(b, NO_FRAME); if (!g.paused()) fed += 80; }
            assertTrue(name + ": audio passed to the Morse decoder during packets: " + fed, fed <= 8000 / 10);   // at most the first flags' 100 ms
            g.flush();
            assertEquals(name, "[]", out.toString());
        }
    }

    @Test public void aDecodedFrameHoldsItForTwoSeconds() {
        short[] id = Morse.pcm("N0CALL", 20, 800);
        assertEquals("[]", gated(id, 10_000, true, g -> g.packetHeard(10_000 - 500)).toString());
        assertEquals("[N0CALL]", gated(id, 10_000, true, g -> g.packetHeard(10_000 - AfskPresence.HOLD_MS - 1)).toString());
    }

    @Test public void aClosedSquelchEndsTheHold() throws Exception {
        short[] packets = java.util.Arrays.copyOf(AfskBenchTest.wav("noise100_flat.wav"), 8000 * 5);
        List<String> out = new ArrayList<>();
        CwGate g = new CwGate(new CwDecoder((t, hz, wpm) -> out.add(t)));
        g.process(packets, NO_FRAME);
        assertTrue(g.paused());
        g.flush();                                                                  // the squelch closed; later a repeater IDs
        g.process(cat(Morse.pcm("N0CALL", 20, 800), new short[8000 * 2]), NO_FRAME);
        assertEquals("[N0CALL]", out.toString());
    }
}
