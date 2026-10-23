/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 *
 * The FT8 encoder, LDPC decoder, waterfall and candidate search follow ft8_lib by Karlis Goba
 * (https://github.com/kgoba/ft8_lib), Copyright (c) 2018 Karlis Goba, MIT License (see COPYRIGHT); the code tables
 * are the protocol's own (WSJT-X, K1JT et al.).
 */
package com.macdmr.radio.ham;

import java.util.ArrayList;
import java.util.List;

/**
 * FT8: 79 symbols of 8-tone GFSK (6.25 Hz apart, 0.16 s each, 12.64 s) carrying 77 bits + CRC-14, LDPC(174,91) coded,
 * three 7x7 Costas sync blocks. Works on 8 kHz audio: here it rides on FM like any other audio mode.
 */
public final class Ft8 {
    private Ft8() { }

    public static final int NN = 79, ND = 58, RATE = 8000, SPS = 1280;          // symbols, data symbols, sample rate, samples per symbol
    public static final double SYMBOL_S = 0.16, TONE_HZ = 6.25, SLOT_S = 15.0, LENGTH_S = NN * SYMBOL_S;
    static final int[] COSTAS = { 3, 1, 4, 0, 6, 5, 2 };
    static final int[] GRAY = { 0, 1, 3, 2, 5, 6, 4, 7 };
    static final int LDPC_N = 174, LDPC_K = 91, LDPC_M = 83;

    /** Parity generator rows, 12 bytes each (MSB first). */
    static final String GEN = ""
        + "8329ce11bf31eaf509f27fc0"
        + "761c264e25c2593354931320"
        + "dc265902fb277c6410a1bdc0"
        + "1b3f417858cd2dd33ec7f620"
        + "09fda4fee04195fd034783a0"
        + "077cccc11b8873ed5c3d48a0"
        + "29b62afe3ca036f4fe1a9da0"
        + "6054faf5f35d96d3b0c8c3e0"
        + "e20798e4310eed27884ae900"
        + "775c9c08e80e26ddae563180"
        + "b0b811028c2bf997213487c0"
        + "18a0c9231fc60adf5c5ea320"
        + "76471e8302a0721e01b12b80"
        + "ffbccb80ca8341fafb47b2e0"
        + "66a72a158f9325a2bf671700"
        + "c4243689fe85b1c51363a180"
        + "0dff739414d1a1b34b1c2700"
        + "15b48830636c8b99894972e0"
        + "29a89c0d3de81d665489b0e0"
        + "4f126f37fa51cbe61bd6b940"
        + "99c47239d0d97d3c84e09400"
        + "1919b75119765621bb4f1e80"
        + "09db12d731faee0b86df6b80"
        + "488fc33df43fbdeea4eafb40"
        + "827423ee40b675f756eb5fe0"
        + "abe197c484cb74757144a9a0"
        + "2b500e4bc0ec5a6d2bdbdd00"
        + "c474aa53d702187616693600"
        + "8eba1a13db3390bd6718cec0"
        + "753844673a27782cc42012e0"
        + "06ff83a145c37035a5c12680"
        + "3b37417858cc2dd33ec3f620"
        + "9a4a5a28ee17ca9c324842c0"
        + "bc29f465309c977e89610a40"
        + "2663ae6ddf8b5ce2bb294880"
        + "46f231efe457034c18144180"
        + "3fb2ce85abe9b0c72e06fbe0"
        + "de87481f282c153971a0a2e0"
        + "fcd7ccf23c69fa99bba14120"
        + "f0261447e9490ca8e474cec0"
        + "4410115818196f95cdd70120"
        + "088fc31df4bfbde2a4eafb40"
        + "b8fef1b6307729fb0a078c00"
        + "5afea7acccb77bbc9d99a900"
        + "49a7016ac653f65ecdc90760"
        + "1944d085be4e7da8d6cc7d00"
        + "251f62adc4032f0ee7140020"
        + "56471f8702a0721e00b12b80"
        + "2b8e4923f2dd51e2d537fa00"
        + "6b550a40a66f4755de95c260"
        + "a18ad28d4e27fe92a4f6c840"
        + "10c2e586388cb82a3d807580"
        + "ef34a41817ee02133db2eb00"
        + "7e9c0c54325a9c15836e0000"
        + "3693e572d1fde4cdf079e860"
        + "bfb2cec5abe1b0c72e07fbe0"
        + "7ee18230c583cccc57d4b080"
        + "a066cb2fedafc9f526641260"
        + "bb23725abc47cc5f4cc4cd20"
        + "ded9dba3bee40c59b5609b40"
        + "d9a7016ac653e6decdc90360"
        + "9ad46aed5f707f280ab5fc40"
        + "e5921c77822587316d7d3c20"
        + "4f14da8242a8b86dca733520"
        + "8b8b507ad467d4441df770e0"
        + "22831c9cf1169467ad04b680"
        + "213b838fe2ae54c38ee71800"
        + "5d926b6dd71f085181a4e120"
        + "66ab79d4b29ee6e69509e560"
        + "958148682d748a38dd68baa0"
        + "b8ce020cf069c32a723ab140"
        + "f4331d6d461607e957527460"
        + "6da23ba424b9596133cf9c80"
        + "a636bcbc7b30c5fbeae67fe0"
        + "5cb0d86a07df654a9089a200"
        + "f11f106848780fc9ecdd80a0"
        + "1fbb5364fb8d2c9d730d5ba0"
        + "fcb86bc70a50c9d02a5d0340"
        + "a534433029eac15f322e34c0"
        + "c989d9c7c3d3b8c55d751300"
        + "7bb38b2f0186d46643ae9620"
        + "2644ebadeb44b9467d1f42c0"
        + "608cc857594bfbb55d696000"
        ;
    /** Parity checks: up to 7 codeword bits (1-based, 0 = unused) per check. */
    static final short[][] NM = {
        {4, 31, 59, 91, 92, 96, 153},
        {5, 32, 60, 93, 115, 146, 0},
        {6, 24, 61, 94, 122, 151, 0},
        {7, 33, 62, 95, 96, 143, 0},
        {8, 25, 63, 83, 93, 96, 148},
        {6, 32, 64, 97, 126, 138, 0},
        {5, 34, 65, 78, 98, 107, 154},
        {9, 35, 66, 99, 139, 146, 0},
        {10, 36, 67, 100, 107, 126, 0},
        {11, 37, 67, 87, 101, 139, 158},
        {12, 38, 68, 102, 105, 155, 0},
        {13, 39, 69, 103, 149, 162, 0},
        {8, 40, 70, 82, 104, 114, 145},
        {14, 41, 71, 88, 102, 123, 156},
        {15, 42, 59, 106, 123, 159, 0},
        {1, 33, 72, 106, 107, 157, 0},
        {16, 43, 73, 108, 141, 160, 0},
        {17, 37, 74, 81, 109, 131, 154},
        {11, 44, 75, 110, 121, 166, 0},
        {45, 55, 64, 111, 130, 161, 173},
        {8, 46, 71, 112, 119, 166, 0},
        {18, 36, 76, 89, 113, 114, 143},
        {19, 38, 77, 104, 116, 163, 0},
        {20, 47, 70, 92, 138, 165, 0},
        {2, 48, 74, 113, 128, 160, 0},
        {21, 45, 78, 83, 117, 121, 151},
        {22, 47, 58, 118, 127, 164, 0},
        {16, 39, 62, 112, 134, 158, 0},
        {23, 43, 79, 120, 131, 145, 0},
        {19, 35, 59, 73, 110, 125, 161},
        {20, 36, 63, 94, 136, 161, 0},
        {14, 31, 79, 98, 132, 164, 0},
        {3, 44, 80, 124, 127, 169, 0},
        {19, 46, 81, 117, 135, 167, 0},
        {7, 49, 58, 90, 100, 105, 168},
        {12, 50, 61, 118, 119, 144, 0},
        {13, 51, 64, 114, 118, 157, 0},
        {24, 52, 76, 129, 148, 149, 0},
        {25, 53, 69, 90, 101, 130, 156},
        {20, 46, 65, 80, 120, 140, 170},
        {21, 54, 77, 100, 140, 171, 0},
        {35, 82, 133, 142, 171, 174, 0},
        {14, 30, 83, 113, 125, 170, 0},
        {4, 29, 68, 120, 134, 173, 0},
        {1, 4, 52, 57, 86, 136, 152},
        {26, 51, 56, 91, 122, 137, 168},
        {52, 84, 110, 115, 145, 168, 0},
        {7, 50, 81, 99, 132, 173, 0},
        {23, 55, 67, 95, 172, 174, 0},
        {26, 41, 77, 109, 141, 148, 0},
        {2, 27, 41, 61, 62, 115, 133},
        {27, 40, 56, 124, 125, 126, 0},
        {18, 49, 55, 124, 141, 167, 0},
        {6, 33, 85, 108, 116, 156, 0},
        {28, 48, 70, 85, 105, 129, 158},
        {9, 54, 63, 131, 147, 155, 0},
        {22, 53, 68, 109, 121, 174, 0},
        {3, 13, 48, 78, 95, 123, 0},
        {31, 69, 133, 150, 155, 169, 0},
        {12, 43, 66, 89, 97, 135, 159},
        {5, 39, 75, 102, 136, 167, 0},
        {2, 54, 86, 101, 135, 164, 0},
        {15, 56, 87, 108, 119, 171, 0},
        {10, 44, 82, 91, 111, 144, 149},
        {23, 34, 71, 94, 127, 153, 0},
        {11, 49, 88, 92, 142, 157, 0},
        {29, 34, 87, 97, 147, 162, 0},
        {30, 50, 60, 86, 137, 142, 162},
        {10, 53, 66, 84, 112, 128, 165},
        {22, 57, 85, 93, 140, 159, 0},
        {28, 32, 72, 103, 132, 166, 0},
        {28, 29, 84, 88, 117, 143, 150},
        {1, 26, 45, 80, 128, 147, 0},
        {17, 27, 89, 103, 116, 153, 0},
        {51, 57, 98, 163, 165, 172, 0},
        {21, 37, 73, 138, 152, 169, 0},
        {16, 47, 76, 130, 137, 154, 0},
        {3, 24, 30, 72, 104, 139, 0},
        {9, 40, 90, 106, 134, 151, 0},
        {15, 58, 60, 74, 111, 150, 163},
        {18, 42, 79, 144, 146, 152, 0},
        {25, 38, 65, 99, 122, 160, 0},
        {17, 42, 75, 129, 170, 172, 0},
    };
    /** For each codeword bit, its three parity checks (1-based). */
    static final short[][] MN = {
        {16, 45, 73}, {25, 51, 62}, {33, 58, 78}, {1, 44, 45}, {2, 7, 61}, {3, 6, 54}, {4, 35, 48}, {5, 13, 21},
        {8, 56, 79}, {9, 64, 69}, {10, 19, 66}, {11, 36, 60}, {12, 37, 58}, {14, 32, 43}, {15, 63, 80}, {17, 28, 77},
        {18, 74, 83}, {22, 53, 81}, {23, 30, 34}, {24, 31, 40}, {26, 41, 76}, {27, 57, 70}, {29, 49, 65}, {3, 38, 78},
        {5, 39, 82}, {46, 50, 73}, {51, 52, 74}, {55, 71, 72}, {44, 67, 72}, {43, 68, 78}, {1, 32, 59}, {2, 6, 71},
        {4, 16, 54}, {7, 65, 67}, {8, 30, 42}, {9, 22, 31}, {10, 18, 76}, {11, 23, 82}, {12, 28, 61}, {13, 52, 79},
        {14, 50, 51}, {15, 81, 83}, {17, 29, 60}, {19, 33, 64}, {20, 26, 73}, {21, 34, 40}, {24, 27, 77}, {25, 55, 58},
        {35, 53, 66}, {36, 48, 68}, {37, 46, 75}, {38, 45, 47}, {39, 57, 69}, {41, 56, 62}, {20, 49, 53}, {46, 52, 63},
        {45, 70, 75}, {27, 35, 80}, {1, 15, 30}, {2, 68, 80}, {3, 36, 51}, {4, 28, 51}, {5, 31, 56}, {6, 20, 37},
        {7, 40, 82}, {8, 60, 69}, {9, 10, 49}, {11, 44, 57}, {12, 39, 59}, {13, 24, 55}, {14, 21, 65}, {16, 71, 78},
        {17, 30, 76}, {18, 25, 80}, {19, 61, 83}, {22, 38, 77}, {23, 41, 50}, {7, 26, 58}, {29, 32, 81}, {33, 40, 73},
        {18, 34, 48}, {13, 42, 64}, {5, 26, 43}, {47, 69, 72}, {54, 55, 70}, {45, 62, 68}, {10, 63, 67}, {14, 66, 72},
        {22, 60, 74}, {35, 39, 79}, {1, 46, 64}, {1, 24, 66}, {2, 5, 70}, {3, 31, 65}, {4, 49, 58}, {1, 4, 5},
        {6, 60, 67}, {7, 32, 75}, {8, 48, 82}, {9, 35, 41}, {10, 39, 62}, {11, 14, 61}, {12, 71, 74}, {13, 23, 78},
        {11, 35, 55}, {15, 16, 79}, {7, 9, 16}, {17, 54, 63}, {18, 50, 57}, {19, 30, 47}, {20, 64, 80}, {21, 28, 69},
        {22, 25, 43}, {13, 22, 37}, {2, 47, 51}, {23, 54, 74}, {26, 34, 72}, {27, 36, 37}, {21, 36, 63}, {29, 40, 44},
        {19, 26, 57}, {3, 46, 82}, {14, 15, 58}, {33, 52, 53}, {30, 43, 52}, {6, 9, 52}, {27, 33, 65}, {25, 69, 73},
        {38, 55, 83}, {20, 39, 77}, {18, 29, 56}, {32, 48, 71}, {42, 51, 59}, {28, 44, 79}, {34, 60, 62}, {31, 45, 61},
        {46, 68, 77}, {6, 24, 76}, {8, 10, 78}, {40, 41, 70}, {17, 50, 53}, {42, 66, 68}, {4, 22, 72}, {36, 64, 81},
        {13, 29, 47}, {2, 8, 81}, {56, 67, 73}, {5, 38, 50}, {12, 38, 64}, {59, 72, 80}, {3, 26, 79}, {45, 76, 81},
        {1, 65, 74}, {7, 18, 77}, {11, 56, 59}, {14, 39, 54}, {16, 37, 66}, {10, 28, 55}, {15, 60, 70}, {17, 25, 82},
        {20, 30, 31}, {12, 67, 68}, {23, 75, 80}, {27, 32, 62}, {24, 69, 75}, {19, 21, 71}, {34, 53, 61}, {35, 46, 47},
        {33, 59, 76}, {40, 43, 83}, {41, 42, 63}, {49, 75, 83}, {20, 44, 48}, {42, 49, 57},
    };
    static final short[] NUM_ROWS = {7, 6, 6, 6, 7, 6, 7, 6, 6, 7, 6, 6, 7, 7, 6, 6, 6, 7, 6, 7, 6, 7, 6, 6, 6, 7, 6, 6, 6, 7, 6, 6, 6, 6, 7, 6, 6, 6, 7, 7, 6, 6, 6, 6, 7, 7, 6, 6, 6, 6, 7, 6, 6, 6, 7, 6, 6, 6, 6, 7, 6, 6, 6, 7, 6, 6, 6, 7, 7, 6, 6, 7, 6, 6, 6, 6, 6, 6, 6, 7, 6, 6, 6};

    private static final byte[][] GENERATOR = new byte[LDPC_M][12];
    static {
        for (int i = 0; i < LDPC_M; i++) for (int j = 0; j < 12; j++) GENERATOR[i][j] = (byte) Integer.parseInt(GEN.substring(24 * i + 2 * j, 24 * i + 2 * j + 2), 16);
    }

    // ---- CRC and encoder ------------------------------------------------------------------------------

    /** CRC-14 (polynomial 0x2757) over the first {@code numBits} bits, MSB first. */
    static int crc14(byte[] msg, int numBits) {
        int rem = 0, idx = 0;
        for (int b = 0; b < numBits; b++) {
            if (b % 8 == 0) rem ^= (msg[idx++] & 0xFF) << 6;
            rem = (rem & 0x2000) != 0 ? ((rem << 1) ^ 0x2757) : (rem << 1);
            rem &= 0xFFFF;
        }
        return rem & 0x3FFF;
    }

    /** 77-bit payload (10 bytes) -> 91 bits with the CRC (12 bytes). The CRC covers the payload zero-extended to 82 bits. */
    static byte[] addCrc(byte[] payload) {
        byte[] a = new byte[12];
        System.arraycopy(payload, 0, a, 0, 10);
        a[9] &= (byte) 0xF8;
        int crc = crc14(a, 82);
        a[9] |= (byte) (crc >> 11); a[10] = (byte) (crc >> 3); a[11] = (byte) (crc << 5);
        return a;
    }

    private static int parity8(int x) { x ^= x >> 4; x ^= x >> 2; x ^= x >> 1; return x & 1; }

    /** 91 bits -> the 174-bit codeword (22 bytes): the message, then 83 parity bits. */
    static byte[] encode174(byte[] a91) {
        byte[] cw = new byte[22];
        System.arraycopy(a91, 0, cw, 0, 12);
        int mask = 0x80 >> (LDPC_K % 8), idx = 11;
        for (int i = 0; i < LDPC_M; i++) {
            int sum = 0;
            for (int j = 0; j < 12; j++) sum ^= parity8((a91[j] & GENERATOR[i][j]) & 0xFF);
            if (sum != 0) cw[idx] |= (byte) mask;
            mask >>= 1; if (mask == 0) { mask = 0x80; idx++; }
        }
        return cw;
    }

    /** The 79 channel tones (0-7) for a 77-bit payload. */
    public static int[] tones(byte[] payload) {
        byte[] cw = encode174(addCrc(payload));
        int[] t = new int[NN];
        int mask = 0x80, ib = 0;
        for (int i = 0; i < NN; i++) {
            if (i < 7) t[i] = COSTAS[i];
            else if (i >= 36 && i < 43) t[i] = COSTAS[i - 36];
            else if (i >= 72) t[i] = COSTAS[i - 72];
            else {
                int b3 = 0;
                for (int k = 2; k >= 0; k--) { if ((cw[ib] & mask) != 0) b3 |= 1 << k; mask >>= 1; if (mask == 0) { mask = 0x80; ib++; } }
                t[i] = GRAY[b3];
            }
        }
        return t;
    }

    // ---- GFSK synthesis -------------------------------------------------------------------------------

    private static double erf(double x) {                                // Abramowitz-Stegun 7.1.26 (error < 1.5e-7)
        double s = Math.signum(x); x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return s * y;
    }

    /** Audio of the tones with the lowest tone at {@code f0} Hz: GFSK, BT 2, short raised-cosine ramps at both ends. */
    public static float[] synth(int[] tones, double f0, int rate) {
        int sps = (int) Math.round(rate * SYMBOL_S), n = tones.length, nWave = n * sps;
        double[] pulse = new double[3 * sps];
        final double k = 5.336446;                                         // pi * sqrt(2 / ln 2)
        for (int i = 0; i < 3 * sps; i++) { double t = i / (double) sps - 1.5; pulse[i] = (erf(k * 2.0 * (t + 0.5)) - erf(k * 2.0 * (t - 0.5))) / 2; }
        double peak = 2 * Math.PI / sps;
        double[] dphi = new double[nWave + 2 * sps];
        java.util.Arrays.fill(dphi, 2 * Math.PI * f0 / rate);
        for (int i = 0; i < n; i++) { int ib = i * sps; for (int j = 0; j < 3 * sps; j++) dphi[j + ib] += peak * tones[i] * pulse[j]; }
        for (int j = 0; j < 2 * sps; j++) { dphi[j] += peak * pulse[j + sps] * tones[0]; dphi[j + n * sps] += peak * pulse[j] * tones[n - 1]; }
        float[] out = new float[nWave];
        double phi = 0;
        for (int i = 0; i < nWave; i++) { out[i] = (float) Math.sin(phi); phi = (phi + dphi[i + sps]) % (2 * Math.PI); }
        int ramp = sps / 8;
        for (int i = 0; i < ramp; i++) { float e = (float) ((1 - Math.cos(2 * Math.PI * i / (2 * ramp))) / 2); out[i] *= e; out[nWave - 1 - i] *= e; }
        return out;
    }

    /** A transmission as 8 kHz PCM: {@code leadMs} of silence first (the radio's TX path settles), then the 12.64 s signal. */
    public static short[] pcm(byte[] payload, double f0, int leadMs, double level) {
        float[] w = synth(tones(payload), f0, RATE);
        int lead = RATE * leadMs / 1000;
        short[] out = new short[lead + w.length];
        for (int i = 0; i < w.length; i++) out[lead + i] = (short) Math.round(w[i] * level * 32767);
        return out;
    }

    // ---- LDPC belief propagation ----------------------------------------------------------------------

    private static float fastTanh(float x) {
        if (x < -4.97f) return -1f;
        if (x > 4.97f) return 1f;
        float x2 = x * x; return x * (945f + x2 * (105f + x2)) / (945f + x2 * (420f + x2 * 15f));
    }
    private static float fastAtanh(float x) { float x2 = x * x; return x * (945f + x2 * (-735f + x2 * 64f)) / (945f + x2 * (-1050f + x2 * 225f)); }

    static int ldpcCheck(byte[] cw) {
        int errors = 0;
        for (int m = 0; m < LDPC_M; m++) { int x = 0; for (int i = 0; i < NUM_ROWS[m]; i++) x ^= cw[NM[m][i] - 1]; if (x != 0) errors++; }
        return errors;
    }

    /** Sum-product decoding of 174 log-likelihoods (positive = 1); returns the remaining parity errors (0 = a codeword). */
    static int bpDecode(float[] llr, int maxIter, byte[] plain) {
        float[][] tov = new float[LDPC_N][3], toc = new float[LDPC_M][7];
        int minErrors = LDPC_M;
        for (int iter = 0; iter < maxIter; iter++) {
            int sum = 0;
            for (int n = 0; n < LDPC_N; n++) { plain[n] = (byte) (llr[n] + tov[n][0] + tov[n][1] + tov[n][2] > 0 ? 1 : 0); sum += plain[n]; }
            if (sum == 0) break;                                           // all zeros: not a real message
            int errors = ldpcCheck(plain);
            if (errors < minErrors) { minErrors = errors; if (errors == 0) break; }
            for (int m = 0; m < LDPC_M; m++) for (int ni = 0; ni < NUM_ROWS[m]; ni++) {
                int n = NM[m][ni] - 1; float t = llr[n];
                for (int mi = 0; mi < 3; mi++) if (MN[n][mi] - 1 != m) t += tov[n][mi];
                toc[m][ni] = fastTanh(-t / 2);
            }
            for (int n = 0; n < LDPC_N; n++) for (int mi = 0; mi < 3; mi++) {
                int m = MN[n][mi] - 1; float t = 1f;
                for (int ni = 0; ni < NUM_ROWS[m]; ni++) if (NM[m][ni] - 1 != n) t *= toc[m][ni];
                tov[n][mi] = -2 * fastAtanh(t);
            }
        }
        return minErrors;
    }

    // ---- waterfall --------------------------------------------------------------------------------------

    static final int OSR = 2;                                              // time and frequency oversampling (half symbols, half tones)

    /** Spectrogram in half-symbol steps and half-tone bins, magnitudes in 0.5 dB steps (0-255) like ft8_lib. */
    public static final class Waterfall {
        public final int numBlocks, numBins, minBin; final int stride; final float[] mag;
        Waterfall(int numBlocks, int numBins, int minBin) { this.numBlocks = numBlocks; this.numBins = numBins; this.minBin = minBin; stride = OSR * OSR * numBins; mag = new float[Math.max(1, numBlocks * stride)]; }
        int index(int block, int timeSub, int freqSub, int bin) { return ((block * OSR + timeSub) * OSR + freqSub) * numBins + bin; }
    }

    /** The waterfall of up to one slot of 8 kHz audio (scaled -1..1), 200-3000 Hz. */
    public static Waterfall waterfall(float[] x, int n) {
        final int block = SPS, sub = block / OSR, nfft = block * OSR;
        int minBin = (int) (200 * SYMBOL_S), maxBin = (int) (3000 * SYMBOL_S) + 1;
        int blocks = Math.min(n / block, (int) (SLOT_S / SYMBOL_S));
        Waterfall wf = new Waterfall(blocks, maxBin - minBin, minBin);
        double[] win = new double[nfft]; double norm = 2.0 / nfft;
        for (int i = 0; i < nfft; i++) { double s = Math.sin(Math.PI * i / nfft); win[i] = norm * s * s; }
        double[] frame = new double[nfft], re = new double[nfft], im = new double[nfft];
        MixedFft fft = new MixedFft(nfft);
        int pos = 0, off = 0;
        for (int b = 0; b < blocks; b++) for (int ts = 0; ts < OSR; ts++) {
            System.arraycopy(frame, sub, frame, 0, nfft - sub);
            for (int i = nfft - sub; i < nfft; i++) frame[i] = x[pos++];
            for (int i = 0; i < nfft; i++) { re[i] = frame[i] * win[i]; im[i] = 0; }
            fft.transform(re, im);
            for (int fs = 0; fs < OSR; fs++) for (int bin = minBin; bin < maxBin; bin++) {
                int src = bin * OSR + fs;
                double db = 10 * Math.log10(1e-12 + re[src] * re[src] + im[src] * im[src]);
                double scaled = 2 * db + 240;
                wf.mag[off++] = (float) (scaled < 0 ? 0 : scaled > 255 ? 255 : Math.floor(scaled));
            }
        }
        return wf;
    }

    // ---- candidates and decoding -------------------------------------------------------------------------

    static final class Candidate { int score, timeOffset, freqOffset, timeSub, freqSub; }

    private static int base(Waterfall wf, Candidate c) { return wf.index(0, c.timeSub, c.freqSub, 0) + c.timeOffset * wf.stride + c.freqOffset; }

    private static int syncScore(Waterfall wf, Candidate c) {
        int score = 0, num = 0, base = base(wf, c);
        for (int m = 0; m < 3; m++) for (int k = 0; k < 7; k++) {
            int blk = 36 * m + k, abs = c.timeOffset + blk;
            if (abs < 0) continue;
            if (abs >= wf.numBlocks) break;
            int p = base + blk * wf.stride, sm = COSTAS[k];
            int v = (int) wf.mag[p + sm];
            if (sm > 0) { score += v - (int) wf.mag[p + sm - 1]; num++; }
            if (sm < 7) { score += v - (int) wf.mag[p + sm + 1]; num++; }
            if (k > 0 && abs > 0) { score += v - (int) wf.mag[p + sm - wf.stride]; num++; }
            if (k + 1 < 7 && abs + 1 < wf.numBlocks) { score += v - (int) wf.mag[p + sm + wf.stride]; num++; }
        }
        return num > 0 ? score / num : 0;
    }

    static List<Candidate> candidates(Waterfall wf, int max, int minScore) {
        java.util.PriorityQueue<Candidate> heap = new java.util.PriorityQueue<>((a, b) -> Integer.compare(a.score, b.score));
        for (int ts = 0; ts < OSR; ts++) for (int fs = 0; fs < OSR; fs++) for (int to = -10; to < 20; to++) for (int fo = 0; fo + 7 < wf.numBins; fo++) {
            Candidate c = new Candidate(); c.timeSub = ts; c.freqSub = fs; c.timeOffset = to; c.freqOffset = fo;
            c.score = syncScore(wf, c);
            if (c.score < minScore) continue;
            if (heap.size() < max) heap.add(c);
            else if (c.score > heap.peek().score) { heap.poll(); heap.add(c); }
        }
        List<Candidate> out = new ArrayList<>(heap);
        out.sort((a, b) -> Integer.compare(b.score, a.score));
        return out;
    }

    private static float mag(Waterfall wf, int i) { return wf.mag[i] * 0.5f - 120f; }
    private static float max4(float a, float b, float c, float d) { return Math.max(Math.max(a, b), Math.max(c, d)); }

    private static void likelihood(Waterfall wf, Candidate c, float[] llr) {
        int base = base(wf, c);
        float[] s = new float[8];
        for (int k = 0; k < ND; k++) {
            int sym = k + (k < 29 ? 7 : 14), bit = 3 * k, blk = c.timeOffset + sym;
            if (blk < 0 || blk >= wf.numBlocks) { llr[bit] = llr[bit + 1] = llr[bit + 2] = 0; continue; }
            int p = base + sym * wf.stride;
            for (int j = 0; j < 8; j++) s[j] = mag(wf, p + GRAY[j]);
            llr[bit] = max4(s[4], s[5], s[6], s[7]) - max4(s[0], s[1], s[2], s[3]);
            llr[bit + 1] = max4(s[2], s[3], s[6], s[7]) - max4(s[0], s[1], s[4], s[5]);
            llr[bit + 2] = max4(s[1], s[3], s[5], s[7]) - max4(s[0], s[2], s[4], s[6]);
        }
        float sum = 0, sum2 = 0;
        for (float v : llr) { sum += v; sum2 += v * v; }
        float var = (sum2 - sum * sum / LDPC_N) / LDPC_N;
        if (var <= 0) return;
        float f = (float) Math.sqrt(24.0 / var);
        for (int i = 0; i < LDPC_N; i++) llr[i] *= f;
    }

    /** One decoded transmission. {@code dtS}: start relative to 0.5 s into the slot (FT8's nominal start); {@code snrDb}: in 2500 Hz, approximate. */
    public static final class Decode {
        public final double freqHz, dtS; public final int snrDb; public final byte[] payload; public final String text;
        Decode(double f, double dt, int snr, byte[] p, String t) { freqHz = f; dtS = dt; snrDb = snr; payload = p; text = t; }
        @Override public String toString() { return String.format(java.util.Locale.US, "%+3d %+4.1f %4.0f %s", snrDb, dtS, freqHz, text); }
    }

    /** Decode one slot of 8 kHz audio (scaled -1..1) that starts at the slot boundary. */
    public static List<Decode> decode(float[] x, int n, Ft8Message.Hashes hashes) {
        Waterfall wf = waterfall(x, n);
        List<Decode> out = new ArrayList<>();
        if (wf.numBlocks < 30) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        float[] llr = new float[LDPC_N]; byte[] plain = new byte[LDPC_N];
        for (Candidate c : candidates(wf, 140, 10)) {
            likelihood(wf, c, llr);
            if (bpDecode(llr, 25, plain) > 0) continue;
            byte[] a91 = new byte[12];
            for (int i = 0; i < LDPC_K; i++) if (plain[i] != 0) a91[i / 8] |= (byte) (0x80 >> (i % 8));
            int crcRx = ((a91[9] & 0x07) << 11) | ((a91[10] & 0xFF) << 3) | ((a91[11] & 0xFF) >> 5);
            a91[9] &= (byte) 0xF8; a91[10] = 0; a91[11] = 0;
            if (crc14(a91, 82) != crcRx) continue;
            byte[] payload = java.util.Arrays.copyOf(a91, 10);
            if (!seen.add(java.util.Arrays.toString(payload))) continue;   // the same message found at a neighbouring time/frequency step
            String text = Ft8Message.unpack(payload, hashes);
            if (text == null) continue;
            double f = (wf.minBin + c.freqOffset + c.freqSub / (double) OSR) / SYMBOL_S;
            double t = (c.timeOffset + c.timeSub / (double) OSR) * SYMBOL_S - 0.5 - SYMBOL_S;   // a window spans two symbols: its best match comes one symbol late (WSJT-X recordings: +0.156 s before this)
            out.add(new Decode(f, t, snr(wf, c, tones(payload)), payload, text));
        }
        return out;
    }

    /** Signal in its tone bin against the other seven bins of each symbol, scaled to a 2500 Hz noise bandwidth (Hann bin: 4.69 Hz). */
    private static int snr(Waterfall wf, Candidate c, int[] tones) {
        int base = base(wf, c);
        double sig = 0, noise = 0; int ns = 0;
        for (int i = 0; i < NN; i++) {
            int blk = c.timeOffset + i; if (blk < 0 || blk >= wf.numBlocks) continue;
            int p = base + i * wf.stride;
            double s = 0, nz = 0;
            for (int j = 0; j < 8; j++) { double pw = Math.pow(10, mag(wf, p + j) / 10); if (j == tones[i]) s = pw; else nz += pw / 7; }
            sig += s; noise += nz; ns++;
        }
        if (ns == 0 || noise <= 0) return -30;
        double r = sig / noise - 1;
        int db = (int) Math.round(10 * Math.log10(Math.max(r, 1e-3)) - 10 * Math.log10(2500 / 4.6875));
        return Math.max(-30, Math.min(49, db));
    }

    /** Complex FFT for sizes made of small factors (the FT8 waterfall at 8 kHz needs 2560 = 2^9 x 5). */
    static final class MixedFft {
        final int n; final double[] cos, sin; final double[] tRe, tIm;
        MixedFft(int n) {
            this.n = n; cos = new double[n]; sin = new double[n];
            for (int i = 0; i < n; i++) { cos[i] = Math.cos(-2 * Math.PI * i / n); sin[i] = Math.sin(-2 * Math.PI * i / n); }
            tRe = new double[n]; tIm = new double[n];
        }
        void transform(double[] re, double[] im) {
            System.arraycopy(re, 0, tRe, 0, n); System.arraycopy(im, 0, tIm, 0, n);
            rec(tRe, tIm, 0, 1, n, re, im, 0);
        }
        private void rec(double[] inR, double[] inI, int off, int stride, int len, double[] outR, double[] outI, int o) {
            if (len == 1) { outR[o] = inR[off]; outI[o] = inI[off]; return; }
            int p = len % 4 == 0 && len != 8 ? 2 : len % 2 == 0 ? 2 : len % 3 == 0 ? 3 : len % 5 == 0 ? 5 : len;
            int m = len / p;
            for (int q = 0; q < p; q++) rec(inR, inI, off + q * stride, stride * p, m, outR, outI, o + q * m);
            int tw = n / len;                                              // twiddle step for this size
            double[] sr = new double[p], si = new double[p];
            for (int k = 0; k < m; k++) {
                for (int q = 0; q < p; q++) {
                    int idx = (q * k * tw) % n;
                    double ar = outR[o + q * m + k], ai = outI[o + q * m + k];
                    sr[q] = ar * cos[idx] - ai * sin[idx]; si[q] = ar * sin[idx] + ai * cos[idx];
                }
                for (int u = 0; u < p; u++) {
                    double xr = 0, xi = 0;
                    for (int q = 0; q < p; q++) { int idx = (q * u * m * tw) % n; xr += sr[q] * cos[idx] - si[q] * sin[idx]; xi += sr[q] * sin[idx] + si[q] * cos[idx]; }
                    outR[o + u * m + k] = xr; outI[o + u * m + k] = xi;
                }
            }
        }
    }
}
