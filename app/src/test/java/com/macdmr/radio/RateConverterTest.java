/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** 32 kHz ↔ 8 kHz for the Atom XL's USB sound card: voice passes at its level, what would alias is filtered out. */
public class RateConverterTest {
    private static byte[] tone(double hz, int rate, int frames, double amp) {
        byte[] b = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            short v = (short) Math.round(amp * Math.sin(2 * Math.PI * hz * i / rate));
            b[4 * i] = (byte) v; b[4 * i + 1] = (byte) (v >> 8); b[4 * i + 2] = (byte) v; b[4 * i + 3] = (byte) (v >> 8);
        }
        return b;
    }

    /** RMS of the left channel, skipping the filter's start-up. */
    private static double rms(byte[] b, int len, int skipFrames) {
        double s = 0; int n = 0;
        for (int i = skipFrames * 4; i + 3 < len; i += 4) { short v = (short) ((b[i] & 0xFF) | (b[i + 1] << 8)); s += v * (double) v; n++; }
        return Math.sqrt(s / n);
    }

    @Test public void decimateKeepsVoiceAndRemovesAliases() {
        RateConverter rc = new RateConverter(4, 32000);
        byte[] in = tone(1000, 32000, 32000, 10000), out = new byte[in.length / 4];
        int n = rc.decimate(in, in.length, out);
        assertEquals(in.length / 4, n);
        double ratio = rms(out, n, 100) / (10000 / Math.sqrt(2));
        assertEquals(1.0, ratio, 0.06);                                          // 1 kHz within ±0.5 dB
        RateConverter rc2 = new RateConverter(4, 32000);
        byte[] hi = tone(7000, 32000, 32000, 10000), o2 = new byte[hi.length / 4];   // would fold to 1 kHz at 8 kHz
        int n2 = rc2.decimate(hi, hi.length, o2);
        assertTrue("7 kHz leak " + rms(o2, n2, 100), rms(o2, n2, 100) < 10000 / Math.sqrt(2) * 0.01);   // > 40 dB down
    }

    @Test public void interpolateKeepsVoiceAndRemovesImages() {
        RateConverter rc = new RateConverter(4, 32000);
        byte[] in = tone(1000, 8000, 8000, 10000), out = new byte[in.length * 4];
        int n = rc.interpolate(in, in.length, out);
        assertEquals(in.length * 4, n);
        assertEquals(1.0, rms(out, n, 400) / (10000 / Math.sqrt(2)), 0.06);
        // the 7 kHz image of a 1 kHz tone: what is left above 4 kHz after removing the 1 kHz component is small
        double s = 0; int k = 0;
        for (int i = 400; i < n / 4; i++) {
            short v = (short) ((out[4 * i] & 0xFF) | (out[4 * i + 1] << 8));
            double clean = 10000 * Math.sin(2 * Math.PI * 1000 * (i - (RateConverter.TAPS_PER_PHASE * 4) / 2.0) / 32000);
            s += (v - clean) * (v - clean); k++;
        }
        assertTrue("residual " + Math.sqrt(s / k), Math.sqrt(s / k) < 10000 * 0.02);
    }

    @Test public void streamingInBlocksEqualsOneCall() {
        byte[] in = tone(700, 32000, 4000, 8000);
        RateConverter a = new RateConverter(4, 32000), b = new RateConverter(4, 32000);
        byte[] whole = new byte[in.length / 4]; a.decimate(in, in.length, whole);
        byte[] parts = new byte[in.length / 4]; int o = 0;
        for (int p = 0; p < in.length; p += 1024) {                              // the helper's pipe hands over odd chunks
            byte[] chunk = java.util.Arrays.copyOfRange(in, p, Math.min(in.length, p + 1024)), t = new byte[chunk.length];
            int m = b.decimate(chunk, chunk.length, t); System.arraycopy(t, 0, parts, o, m); o += m;
        }
        assertArrayEquals(whole, parts);
    }

    @Test public void factorOneIsUntouched() {
        byte[] in = tone(1000, 8000, 800, 12000), out = new byte[in.length];
        assertEquals(in.length, new RateConverter(1, 8000).decimate(in, in.length, out));
        assertArrayEquals(in, out);
    }

    @Test public void commandLineCarriesTheRate() {
        DeviceProfile.DirectAudio usb = new DeviceProfile.DirectAudio(1, 0, 0, new String[0], new String[0], new String[0], new String[0]);
        usb.rate = 32000;
        assertTrue(AlsaDirectAudio.command("/x/sdalsa", true, usb).contains(" cap -c 1 -d 0 -r 32000 -n 2 -p 1024 -k 4 --"));
        DeviceProfile.DirectAudio codec = new DeviceProfile.DirectAudio(0, 11, 8, new String[0], new String[0], new String[0], new String[0]);
        assertTrue(AlsaDirectAudio.command("/x/sdalsa", true, codec).contains(" -r 8000 -n 2 -p 256 -k 4"));   // the Armor's recipe as before
        assertTrue(usb.valid());
        usb.rate = 44100; assertFalse(usb.valid());
    }
}
