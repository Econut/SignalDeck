/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

/**
 * Is packet audio (Bell 202 AFSK) being received right now? A small streaming detector on the 8 kHz receive audio that
 * looks for HDLC flags, the 0x7E bytes every AX.25 transmission starts with (the lead-in) and ends with. It answers at
 * once, block by block, where the full packet decoder only finishes a 2-second window later.
 *
 * Used to pause the Morse decoder while packets are heard: through the radio, pre-emphasis, the limiter and the
 * filters put energy below 1100 Hz into the AFSK audio, which the Morse decoder could read as keyed text.
 *
 * Mark and space energy over one bit, each relative to its own recent peak (the receive audio tilts one tone up or
 * down), a simple clock that locks on the bit edges, NRZI, and a byte register. {@link #RUN} flags whole bytes apart
 * within {@link #SPAN} bits count, so a bit error that spoils one flag between them does not matter. A lead-in has
 * dozens of flags; the host test finds no false run in about 25 minutes of noise, voice-like audio and Morse, and a false
 * run would only pause Morse for {@link #HOLD_MS} ms.
 */
public final class AfskPresence {
    private static final int SR = 8000, N = 7;                // one bit is 6.67 samples
    static final int RUN = 4, SPAN = 48;
    public static final int HOLD_MS = 2000;

    private static final double[] MC = new double[40], MS = new double[40], SC = new double[40], SS = new double[40];   // both tones repeat every 40 samples
    static { for (int i = 0; i < 40; i++) { MC[i] = Math.cos(2 * Math.PI * 1200 * i / SR); MS[i] = -Math.sin(2 * Math.PI * 1200 * i / SR); SC[i] = Math.cos(2 * Math.PI * 2200 * i / SR); SS[i] = -Math.sin(2 * Math.PI * 2200 * i / SR); } }
    private final double[] mr = new double[N], mi = new double[N], sr = new double[N], si = new double[N];
    private double mR, mI, sR, sI, mPk, sPk;
    private int k; private long n;
    private double phase; private boolean level; private double lastD;
    private int reg; private long bits;
    private final long[] flagAt = { -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000 }; private int flagPos;   // bit numbers of the last flags
    private long lastFlagSample = Long.MIN_VALUE / 2;
    private volatile long packetAtMs;

    /** 8 kHz mono 16-bit little-endian, as the receive audio arrives. */
    public void process(byte[] mono, int len) {
        for (int i = 0; i + 1 < len; i += 2) sample((short) ((mono[i] & 0xFF) | (mono[i + 1] << 8)));
    }
    public void process(short[] pcm) { for (short s : pcm) sample(s); }

    /** The packet decoder delivered a frame (any thread): packet audio counts as heard from now. */
    public void packetHeard(long nowMs) { packetAtMs = nowMs; }

    /** Flags were seen within the last {@link #HOLD_MS} ms of audio, or a frame was decoded within that long. */
    public boolean active(long nowMs) { return n - lastFlagSample < (long) SR * HOLD_MS / 1000 || nowMs - packetAtMs < HOLD_MS; }

    /** The audio stopped (squelch closed or channel changed): flags heard so far do not carry over to the next signal. */
    public void reset() { java.util.Arrays.fill(flagAt, -1000); reg = 0; lastFlagSample = n - (long) SR * HOLD_MS / 1000; }

    private void sample(short s) {
        int j = (int) (n % 40); double x = s;
        double a = x * MC[j], b = x * MS[j], c = x * SC[j], d = x * SS[j];
        mR += a - mr[k]; mI += b - mi[k]; sR += c - sr[k]; sI += d - si[k];
        mr[k] = a; mi[k] = b; sr[k] = c; si[k] = d; k = (k + 1) % N;
        n++;
        double m = Math.hypot(mR, mI), sp = Math.hypot(sR, sI);
        mPk = m > mPk ? m : mPk * 0.9995; sPk = sp > sPk ? sp : sPk * 0.9995;          // each tone's level: its recent peak (a flag run is 7/8 one tone)
        double v = m / Math.max(mPk, 1) - sp / Math.max(sPk, 1);
        boolean lv = v > 0;
        if ((v > 0) != (lastD > 0)) phase += (phase < 0.5 ? -phase : 1 - phase) * 0.4;   // a bit edge: pull the clock towards it
        lastD = v;
        double before = phase; phase += 1200.0 / SR;
        if (phase >= 1) phase -= 1;
        if (before < 0.5 && phase >= 0.5) bit(lv);                                        // the middle of a bit
    }

    private void bit(boolean lv) {
        boolean b = lv == level; level = lv;                                               // NRZI: no change is a 1
        reg = ((reg >> 1) | (b ? 0x80 : 0)) & 0xFF;
        bits++;
        if (reg == 0x7E) {
            int aligned = 1;                                                               // earlier flags a whole number of bytes back, within 6 bytes
            for (long f : flagAt) if (bits - f > 0 && bits - f <= SPAN && (bits - f) % 8 == 0) aligned++;
            flagAt[flagPos] = bits; flagPos = (flagPos + 1) % flagAt.length;
            if (aligned >= RUN) lastFlagSample = n;
        }
    }
}
