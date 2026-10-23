/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

/** Codec 2 (David Rowe VK5DGR and contributors, LGPL-2.1), built from source into libcodec2jni: M17 voice is mode 3200. */
public final class Codec2 implements AutoCloseable {
    public static final int MODE_3200 = 0;
    private static boolean loaded;
    private static synchronized void load() { if (!loaded) { System.loadLibrary("codec2jni"); loaded = true; } }

    private long h;
    public final int samples, bytes;
    public Codec2(int mode) { load(); h = create(mode); if (h == 0) throw new IllegalStateException("codec2_create failed"); samples = samplesPerFrame(h); bytes = bytesPerFrame(h); }
    /** One frame of bits (8 bytes in 3200) -> 160 samples at 8 kHz (20 ms). */
    public short[] decode(byte[] frame) { short[] out = new short[samples]; decode(h, frame, out); return out; }
    public byte[] encode(short[] pcm) { byte[] b = new byte[bytes]; encode(h, pcm, b); return b; }
    @Override public synchronized void close() { if (h != 0) { destroy(h); h = 0; } }

    private static native long create(int mode);
    private static native void destroy(long h);
    private static native int samplesPerFrame(long h);
    private static native int bytesPerFrame(long h);
    private static native void decode(long h, byte[] bits, short[] out);
    private static native void encode(long h, short[] in, byte[] bits);
}
