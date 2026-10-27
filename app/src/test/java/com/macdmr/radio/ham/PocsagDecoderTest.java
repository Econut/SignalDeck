/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Collections;

/** Clean 8 kHz two-level audio of a POCSAG batch, at each standard rate. */
public class PocsagDecoderTest {
    @Test public void eachRateReturnsThePage() {
        Pocsag.Page page = new Pocsag.Page(123456, 3, "HELLO");
        for (int baud : PocsagDecoder.RATES) {
            short[] audio = audio(Pocsag.bits(Collections.singletonList(page)), baud);
            PocsagDecoder.Hit hit = PocsagDecoder.best(audio);
            assertEquals(baud, hit.baud);
            assertEquals(1, hit.pages.size());
            assertEquals(123456, hit.pages.get(0).ric);
            assertEquals(3, hit.pages.get(0).func);
            assertEquals("HELLO", hit.pages.get(0).text);
        }
    }

    @Test public void thirtyPartsPerMillionStillDecodes() {
        Pocsag.Page page = new Pocsag.Page(123456, 3, "HELLO");
        int[] bits = Pocsag.bits(java.util.Collections.singletonList(page));
        double spb = 8000.0 / 1200.0 * (1 + 30e-6);
        int n = (int) Math.ceil(bits.length * spb);
        short[] a = new short[n];
        for (int i = 0; i < n; i++) { int b = Math.min(bits.length - 1, (int) (i / spb)); a[i] = (short) (bits[b] == 1 ? -8000 : 8000); }
        PocsagDecoder.Hit hit = PocsagDecoder.best(a);
        assertEquals(1200, hit.baud);
        assertEquals("HELLO", hit.pages.get(0).text);
    }

    @Test public void oneAndTwoBitErrorsAreRepaired() {
        int cw = Pocsag.addressWord(123456, 3);
        assertEquals(cw, Pocsag.correct(cw ^ (1 << 4)));
        assertEquals(cw, Pocsag.correct(cw ^ (1 << 4) ^ (1 << 17)));
        assertFalse(Pocsag.check(cw ^ (1 << 3) ^ (1 << 8) ^ (1 << 19)));
    }

    @Test public void threeErrorsInTheAddressDoNotInventARic() {
        int addr = Pocsag.addressWord(1001, 3);
        int bad = addr ^ (1 << 5) ^ (1 << 12) ^ (1 << 20);
        assertFalse(Pocsag.check(Pocsag.correct(bad)));
        int[] msg = messageBits("PAGE OK");
        int[] bits = new int[32 + 32 + msg.length];
        put(bits, 0, Pocsag.SYNC);
        put(bits, 32, bad);
        System.arraycopy(msg, 0, bits, 64, msg.length);
        java.util.List<Pocsag.Page> pages = Pocsag.decode(bits);
        assertEquals(0, pages.size());
    }

    @Test public void aDroopingBaselineStillDecodesAt512() {
        Pocsag.Page page = new Pocsag.Page(1001, 3, "PAGE 512");
        int[] bits = Pocsag.bits(java.util.Collections.singletonList(page));
        double spb = 8000.0 / 512.0;
        int n = (int) Math.ceil(bits.length * spb);
        short[] a = new short[n];
        for (int i = 0; i < n; i++) {
            int b = Math.min(bits.length - 1, (int) (i / spb));
            double droop = 4000 * Math.exp(-(i % spb) / (spb * 0.45));
            a[i] = (short) ((bits[b] == 1 ? -8000 : 8000) + droop);
        }
        PocsagDecoder.Hit hit = PocsagDecoder.best(a);
        assertEquals(512, hit.baud);
        assertEquals(1001, hit.pages.get(0).ric);
        assertEquals("PAGE 512", hit.pages.get(0).text);
    }

    @Test public void generator512WavRicDependsOnTheThreshold() throws Exception {
        java.io.File f = new java.io.File("D:/Documents/code/personal/phonedmrapp/tools/pocsag-research");
        java.io.File wav = new java.io.File("C:/Users/Joshua/AppData/Local/Temp/fw1718/caps/f5120.wav");
        if (!wav.isFile()) {
            Process p = new ProcessBuilder("python", "pocsag.py", "gen", wav.getAbsolutePath().replace(".wav", ""),
                    "--baud", "512", "--dev", "2500", "--ric", "1001", "--msg", "PAGE 512 A", "--repeat", "1")
                    .directory(f).redirectErrorStream(true).start();
            p.waitFor();
        }
        if (!wav.isFile()) return;
        short[] audio = everySixth(wav(wav));
        Pocsag.Page neu = firstPage(audio, false);
        assertNotNull(neu);
        assertEquals(1001, neu.ric);
        assertTrue(neu.text.startsWith("PAGE 512"));
    }

    private static Pocsag.Page firstPage(short[] audio, boolean runningMean) {
        PocsagDecoder.Clock clock = PocsagDecoder.preamble(audio);
        assertNotNull(clock);
        for (int pol : new int[] { 1, -1 }) {
            int[] bits = PocsagDecoder.follow(audio, clock.spb, clock.phase, pol, runningMean);
            for (Pocsag.Page p : Pocsag.decode(bits)) if (!p.text.isEmpty() && !p.ricUnreadable) return p;
        }
        return null;
    }

    private static short[] everySixth(short[] in) {
        short[] o = new short[in.length / 6];
        for (int i = 0; i < o.length; i++) o[i] = in[i * 6];
        return o;
    }

    @Test public void heldClipRestoresRic1001() throws Exception {
        java.io.File f = new java.io.File("D:/Documents/code/personal/phonedmrapp/tools/pocsag-research/rx/recordings-armor26/rx_p512_held.wav");
        if (!f.isFile()) return;
        short[] raw = wav(f);
        PocsagDecoder.Clock clock = PocsagDecoder.preamble(raw);
        short[] audio = PocsagDecoder.restore(raw, 0.0016);
        PocsagDecoder.Hit hit = PocsagDecoder.best(audio);
        PocsagDecoder.Hit gated = PocsagDecoder.best(raw, 0.0016);
        boolean ok = false;
        for (Pocsag.Page p : hit.pages) if (p.ric == 1001 && p.text.startsWith("ALPHA")) ok = true;
        assertTrue("baud " + hit.baud + " pages " + hit.pages.size(), ok);
        boolean gatedOk = false;
        for (Pocsag.Page p : gated.pages) if (p.ric == 1001 && p.text.startsWith("ALPHA")) gatedOk = true;
        assertTrue("gate spb " + (clock == null ? "none" : clock.spb) + " baud " + gated.baud, gatedOk);
    }

    @Test public void heldTapsKeepTheFasterRatesExact() throws Exception {
        double[] taps = armorTaps();
        assertClip("rx_g1200.wav", taps, 1200, 1002, "STILL 1200");
        assertClip("rx_g2400.wav", taps, 2400, 1003, "STILL 2400");
        java.io.File slow = new java.io.File("D:/Documents/code/personal/phonedmrapp/tools/pocsag-research/rx/recordings-armor26/rx_p512_held.wav");
        if (!slow.isFile()) return;
        PocsagDecoder.Hit hit = PocsagDecoder.best(wav(slow), 0.0016, taps);
        boolean ok = false;
        for (Pocsag.Page p : hit.pages) if (p.ric == 1001 && p.text.startsWith("ALPHA")) ok = true;
        assertTrue("512 baud must not take the tap path " + hit.baud, ok);
    }

    private static void assertClip(String name, double[] taps, int baud, int ric, String text) throws Exception {
        java.io.File f = new java.io.File("D:/Documents/code/personal/phonedmrapp/tools/pocsag-research/rx/recordings-armor26/" + name);
        if (!f.isFile()) return;
        PocsagDecoder.Hit hit = PocsagDecoder.best(wav(f), 0.0016, taps);
        assertEquals(baud, hit.baud);
        assertFalse(hit.pages.isEmpty());
        assertEquals(ric, hit.pages.get(0).ric);
        assertEquals(text, hit.pages.get(0).text);
    }

    private static double[] armorTaps() throws Exception {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(
                "D:/Documents/code/personal/SignalDeck/app/src/main/assets/m17/held_ulefone-armor26ultra.txt"));
        double[] t = new double[lines.size()];
        for (int i = 0; i < t.length; i++) t[i] = Double.parseDouble(lines.get(i).trim());
        return t;
    }

    @Test public void restorationLeaves1200Alone() {
        Pocsag.Page page = new Pocsag.Page(1002, 3, "STILL 1200");
        short[] audio = audio(Pocsag.bits(Collections.singletonList(page)), 1200);
        PocsagDecoder.Hit hit = PocsagDecoder.best(audio, 0.0016);
        assertEquals(1200, hit.baud);
        assertEquals(1, hit.pages.size());
        assertEquals(1002, hit.pages.get(0).ric);
        assertEquals("STILL 1200", hit.pages.get(0).text);
    }

    @Test public void armorPageRecordingDecodes() throws Exception {
        java.io.File f = new java.io.File("D:/Documents/code/personal/phonedmrapp/tools/pocsag-research/rx/recordings-armor26/rx_p2500.wav");
        if (!f.isFile()) return;
        short[] audio = wav(f);
        PocsagDecoder.Hit hit = PocsagDecoder.best(audio);
        for (Pocsag.Page p : hit.pages) if (!p.ricUnreadable) assertEquals(1234567, p.ric);
    }

    private static int[] messageBits(String text) {
        java.util.List<Integer> words = Pocsag.messageWords(text);
        int[] bits = new int[words.size() * 32];
        for (int i = 0; i < words.size(); i++) put(bits, i * 32, words.get(i));
        return bits;
    }

    private static void put(int[] bits, int at, int word) {
        for (int i = 0; i < 32; i++) bits[at + i] = (word >>> (31 - i)) & 1;
    }

    private static short[] wav(java.io.File f) throws Exception {
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        int data = 12;
        while (!(b[data] == 'd' && b[data + 1] == 'a' && b[data + 2] == 't' && b[data + 3] == 'a')) data += 8 + ((b[data + 4] & 0xFF) | ((b[data + 5] & 0xFF) << 8));
        int n = ((b[data + 4] & 0xFF) | ((b[data + 5] & 0xFF) << 8) | ((b[data + 6] & 0xFF) << 16)) / 2;
        short[] a = new short[Math.min(n, (b.length - data - 8) / 2)];
        for (int i = 0; i < a.length; i++) a[i] = (short) ((b[data + 8 + 2 * i] & 0xFF) | (b[data + 9 + 2 * i] << 8));
        return a;
    }

    /** Bit 1 is the negative level, matching the slicer. */
    static short[] audio(int[] bits, int baud) {
        double spb = 8000.0 / baud;
        int n = (int) Math.ceil(bits.length * spb);
        short[] a = new short[n];
        for (int i = 0; i < n; i++) {
            int b = Math.min(bits.length - 1, (int) (i / spb));
            a[i] = (short) (bits[b] == 1 ? -8000 : 8000);
        }
        return a;
    }
}
