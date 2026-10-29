/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

/**
 * Integer-factor sample rate conversion of interleaved stereo 16-bit little-endian PCM, for a radio sound card that
 * runs faster than the app's 8 kHz (the Atom XL's USB card: 32 kHz). One windowed-sinc low-pass (cutoff 3.4 kHz, the
 * voice band the radio passes) serves both directions: before keeping every {@code factor}-th frame on receive, and
 * after inserting {@code factor - 1} zero frames per frame on transmit. Keeps its filter history between calls, so a
 * stream can be fed in blocks of any size.
 */
final class RateConverter {
    static final int TAPS_PER_PHASE = 16;

    final int factor;
    private final float[] h;                    // low-pass at the high rate, unity gain at DC
    private final float[] histL, histR;         // the last h.length frames at the high rate
    private int pos, phase;

    RateConverter(int factor, int highRate) {
        if (factor < 1) throw new IllegalArgumentException("factor " + factor);
        this.factor = factor;
        int n = factor == 1 ? 1 : TAPS_PER_PHASE * factor + 1;
        h = new float[n];
        double fc = 3400.0 / highRate, sum = 0;
        for (int i = 0; i < n; i++) {
            double m = i - (n - 1) / 2.0;
            double sinc = m == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * m) / (Math.PI * m);
            double w = n == 1 ? 1 : 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (n - 1));   // Blackman
            h[i] = (float) (sinc * w); sum += h[i];
        }
        for (int i = 0; i < n; i++) h[i] /= (float) sum;
        histL = new float[n]; histR = new float[n];
    }

    private void push(float l, float r) { histL[pos] = l; histR[pos] = r; pos = (pos + 1) % h.length; }

    private float out(float[] hist) {
        float acc = 0; int k = pos;
        for (int i = h.length - 1; i >= 0; i--) { acc += h[i] * hist[k]; k = (k + 1) % h.length; }
        return acc;
    }

    private static short clip(float v) { return (short) Math.max(-32768, Math.min(32767, Math.round(v))); }

    /** High-rate stereo bytes in, 8 kHz stereo bytes out; returns the bytes written to {@code out} (len / factor, rounded to frames). */
    int decimate(byte[] in, int len, byte[] out) {
        int o = 0;
        for (int i = 0; i + 3 < len; i += 4) {
            push((short) ((in[i] & 0xFF) | (in[i + 1] << 8)), (short) ((in[i + 2] & 0xFF) | (in[i + 3] << 8)));
            if (++phase < factor) continue;
            phase = 0;
            short l = clip(out(histL)), r = clip(out(histR));
            out[o++] = (byte) l; out[o++] = (byte) (l >> 8); out[o++] = (byte) r; out[o++] = (byte) (r >> 8);
        }
        return o;
    }

    /** 8 kHz stereo bytes in, high-rate stereo bytes out; returns the bytes written to {@code out} (len × factor). */
    int interpolate(byte[] in, int len, byte[] out) {
        int o = 0;
        for (int i = 0; i + 3 < len; i += 4) {
            float l = (short) ((in[i] & 0xFF) | (in[i + 1] << 8)), r = (short) ((in[i + 2] & 0xFF) | (in[i + 3] << 8));
            for (int k = 0; k < factor; k++) {
                push(k == 0 ? l * factor : 0, k == 0 ? r * factor : 0);   // zero-stuffed: ×factor keeps the level
                short ol = clip(out(histL)), or = clip(out(histR));
                out[o++] = (byte) ol; out[o++] = (byte) (ol >> 8); out[o++] = (byte) or; out[o++] = (byte) (or >> 8);
            }
        }
        return o;
    }
}
