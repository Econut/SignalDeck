/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

/**
 * M17 receiver for this radio's 8 kHz receive audio. The radio's audio path removes everything below about 250 Hz, where
 * M17 4FSK carries much of its signal, so a plain demodulator decodes nothing. This one equalizes: a decision-feedback
 * equalizer (feed-forward taps at half-symbol spacing with cubic terms for the receiver's compression, plus the last 24
 * symbols fed back, which rebuild the missing low frequencies), calibrated per phone, with the symbol timing re-chosen
 * every 40 ms frame and the coefficients slowly adapted on frames the error correction confirms.
 * A transmission is found by its preamble (a 40 ms 2400 Hz tone, which the radio passes), then the LSF sync word.
 */
public final class M17Receiver {
    public interface Listener {
        void started(M17.Lsf lsf);                    // after the LSF (crcOk tells whether it is reliable)
        void frame(M17.Stream s, float quality);       // every stream frame (payload = two Codec 2 3200 frames)
        void lsfFromLich(M17.Lsf lsf);                 // the LSF rebuilt from six LICH chunks (late or damaged LSF)
        void ended(int frames, int good, String why);
        /** A packet whose CRC checked. The body is the type byte and the payload, without the CRC. */
        default void packet(M17.Lsf lsf, byte[] body) { }
        /** The last frame arrived and the packet CRC failed, or a frame in the run was missing. */
        default void framesLost() { }
    }

    public static final int L = 8, FB = 24, GROUPS = 4, NCOEF = GROUPS * (2 * L + 1) + FB + 1;
    static final int UP = 6, SPS48 = 10, HALF = 16;              // 8 -> 48 kHz; 10 samples a symbol; interpolator half length (input samples)
    private static final float[] INTERP = new float[UP * 2 * HALF + 1];
    static {
        int n = INTERP.length, c = n / 2;
        for (int i = 0; i < n; i++) {
            double t = (i - c) / (double) UP, s = t == 0 ? 1 : Math.sin(Math.PI * t) / (Math.PI * t);
            double w = 0.42 + 0.5 * Math.cos(Math.PI * (i - c) / (c + 1.0)) + 0.08 * Math.cos(2 * Math.PI * (i - c) / (c + 1.0));
            INTERP[i] = (float) (s * w);
        }
    }

    private final float[] coef;
    private final Listener listener;
    public volatile float adaptRate = 0f;                        // NLMS on confirmed frames: off (on the bench recordings it pulled the timing off; 299/300 frames exact without it)

    // the audio at 48 kHz in a ring; absolute index = 48 kHz samples since start
    private static final int RING = 48000 * 8;
    private final float[] x48 = new float[RING];
    private final float[] x8 = new float[2 * HALF + 1];          // the last input samples, for the interpolator
    private long n8, n48;                                        // samples received (8 kHz) / produced (48 kHz)

    // preamble detector (Goertzel at 2400 Hz on the 8 kHz audio, 20 ms windows every 10 ms)
    private final float[] win = new float[160]; private int winFill;
    private int strong; private long preStart48 = -1, preEnd48 = -1;

    private enum State { IDLE, ACQUIRE, STREAM, PACKET }
    static final int SEARCH = 60;                                // symbols either side of the estimated preamble end
    private State state = State.IDLE;
    private java.util.List<M17.Packet> packets;
    private long next48;                                         // where the next frame's first sync symbol is expected
    private float gain = 1;
    private final float[] past = new float[FB];
    private int frames, good, lost;
    private M17.Lsf lsf;
    private final byte[][] lichChunks = new byte[6][]; private boolean lsfFromLichSent;

    public M17Receiver(float[] coef, Listener l) { if (coef.length != NCOEF) throw new IllegalArgumentException("coefficients: " + coef.length); this.coef = coef.clone(); listener = l; }
    public float[] coefficients() { return coef.clone(); }
    public boolean receiving() { return state != State.IDLE; }

    /** 16-bit little-endian mono audio at 8 kHz (as the radio delivers it). */
    public void feed(byte[] mono, int n) { for (int i = 0; i + 1 < n; i += 2) push((short) ((mono[i] & 0xFF) | (mono[i + 1] << 8))); }
    public void feed(short[] pcm, int n) { for (int i = 0; i < n; i++) push(pcm[i]); }

    /** 8 kHz sample time t (since start) -> its index in the 48 kHz stream (the interpolator lags HALF input samples). */
    static long to48(long t8) { return t8 * UP + (long) HALF * UP; }
    private void interpolate(float v) {
        System.arraycopy(x8, 1, x8, 0, x8.length - 1); x8[x8.length - 1] = v; n8++;
        for (int ph = 0; ph < UP; ph++) {                            // the 6 outputs between input times n-HALF and n-HALF+5/6
            float s = 0;
            for (int j = 0; j < x8.length; j++) { int ti = UP * j + ph; if (ti < INTERP.length) s += x8[x8.length - 1 - j] * INTERP[INTERP.length - 1 - ti]; }
            x48[(int) (n48 % RING)] = s; n48++;
        }
    }
    private void push(float v) {
        interpolate(v);
        preamble(v);
        if (state == State.ACQUIRE && n48 > preEnd48 + (SEARCH + 192 + 4) * SPS48 + 5 * L + 60) acquire();
        while (state == State.STREAM && n48 > next48 + 192 * SPS48 + 5 * L + 60) frame();
        while (state == State.PACKET && n48 > next48 + 192 * SPS48 + 5 * L + 60) packetFrame();
    }
    private float at(long i) { return i < 0 || i >= n48 || n48 - i >= RING ? 0f : x48[(int) (i % RING)]; }

    private void preamble(float v) {
        win[winFill++] = v;
        if (winFill < win.length) return;
        double c = 2 * Math.cos(2 * Math.PI * 2400 / 8000.0), s1 = 0, s2 = 0, e = 0;
        for (float x : win) { double s = x + c * s1 - s2; s2 = s1; s1 = s; e += x * x; }
        double p = s1 * s1 + s2 * s2 - c * s1 * s2, r = e > 1e-9 ? 2 * p / (win.length * e) : 0;
        System.arraycopy(win, 80, win, 0, 80); winFill = 80;
        long centre48 = to48(n8 - 80);                               // the window's centre in the 48 kHz stream
        if (r > 0.6) { if (strong == 0) preStart48 = centre48; strong++; preEnd48 = to48(n8 - 40); }
        else if (strong > 0) {
            if (strong >= 2 && state == State.IDLE) state = State.ACQUIRE;
            strong = 0;
        }
    }

    // ---- equalizer ---------------------------------------------------------------------------------------

    private final float[] f = new float[NCOEF];
    /** Features of the symbol centred at 48 kHz index p, with the decision history `hist` (most recent first). */
    private void features(long p, float[] hist) {
        int j = 0, n = 2 * L + 1;
        for (int m = -L; m <= L; m++) {
            float v = at(p + 5L * m) / gain;
            f[j] = v; f[j + n] = v * v * v; f[j + 2 * n] = Math.abs(v) * v; f[j + 3 * n] = v * v * v * v * v; j++;
        }
        System.arraycopy(hist, 0, f, GROUPS * n, FB); f[NCOEF - 1] = 1f;
    }
    private float out() { float y = 0; for (int i = 0; i < NCOEF; i++) y += coef[i] * f[i]; return y; }
    static float slice(float y) { return y >= 2 ? 3 : y >= 0 ? 1 : y >= -2 ? -1 : -3; }

    /** Equalize `n` symbols from p0 with the history `hist` (updated); returns the summed decision error, outputs in ys. */
    private float run(long p0, int n, float[] hist, float[] ys, int off) {
        float err = 0;
        for (int k = 0; k < n; k++) {
            features(p0 + (long) k * SPS48, hist);
            float y = out(), d = slice(y); if (ys != null) ys[off + k] = y; err += (y - d) * (y - d);
            System.arraycopy(hist, 0, hist, 1, FB - 1); hist[0] = d;
        }
        return err;
    }

    // ---- acquisition: the LSF sync word right after the preamble -------------------------------------------

    private void acquire() {
        // level: the preamble's own RMS (calibrated the same way)
        double e = 0; int cnt = 0;
        for (long i = preStart48 + 20 * UP; i < preEnd48 - 40 * UP; i++) { float v = at(i); e += v * v; cnt++; }
        gain = (float) Math.sqrt(e / Math.max(1, cnt)); if (gain < 1e-3f) { reset("no level"); return; }
        int[] sync = new int[8]; for (int i = 0; i < 8; i++) sync[i] = M17.SYMBOL_MAP[(M17.SYNC_LSF >> (14 - 2 * i)) & 3];
        long bestP = -1; float bestScore = Float.MAX_VALUE; float[] h = new float[FB]; float[] ys = new float[8];
        for (long p = preEnd48 - SEARCH * SPS48; p <= preEnd48 + SEARCH * SPS48; p++) {
            for (int i = 0; i < FB; i++) h[i] = (i % 2 == 0) ? -3 : 3;      // the preamble: ... +3 -3, the last one -3
            float err = run(p, 8, h, ys, 0); int miss = 0;
            for (int i = 0; i < 8; i++) if (slice(ys[i]) != sync[i]) miss++;
            float score = miss * 100 + err;
            if (score < bestScore) { bestScore = score; bestP = p; }
        }
        if (bestP < 0 || bestScore >= 200) { reset("no LSF sync after the preamble"); return; }
        for (int i = 0; i < FB; i++) past[i] = (i % 2 == 0) ? -3 : 3;
        float[] sym = new float[192]; trackFrame(bestP, sym);
        lsf = M17.decodeLsf(sym, 8);
        frames = 0; good = 0; lost = 0; java.util.Arrays.fill(lichChunks, null); lsfFromLichSent = false;
        listener.started(lsf);
        state = lsf != null && lsf.crcOk && !lsf.stream() ? State.PACKET : State.STREAM;
        if (state == State.PACKET) packets = new java.util.ArrayList<>();
    }

    /** One frame at p0 with timing re-chosen among p0-2..p0+2 (48 kHz samples); returns the chosen start, symbols in sym. */
    private long chosen;
    private float[] frameFeat;                                   // the chosen frame's features, for adaptation
    private float trackFrame(long p0, float[] sym) {
        float best = Float.MAX_VALUE; long bp = p0; float[] bh = null, bys = null;
        for (long p = p0 - 2; p <= p0 + 2; p++) {
            float[] h = past.clone(), ys = new float[192]; float err = run(p, 192, h, ys, 0);
            if (err < best) { best = err; bp = p; bh = h; bys = ys; }
        }
        System.arraycopy(bys, 0, sym, 0, 192); System.arraycopy(bh, 0, past, 0, FB); chosen = bp;
        next48 = bp + 192 * SPS48;
        return best / 192;
    }

    private void frame() {
        long p0 = next48; float[] before = past.clone();
        float[] sym = new float[192]; float mse = trackFrame(p0, sym);
        int sm = 0, em = 0;
        for (int i = 0; i < 8; i++) { int s = (int) slice(sym[i]); if (s == M17.SYMBOL_MAP[(M17.SYNC_STR >> (14 - 2 * i)) & 3]) sm++; if (s == M17.EOT_SYMBOLS[i]) em++; }
        if (em >= 7 && em > sm) { end("end of transmission"); return; }
        if (sm < 6) { if (++lost >= 2) { end("signal lost"); return; } return; }
        lost = 0; frames++;
        M17.Stream s = M17.decodeStream(sym, 8);
        float quality = Math.max(0, Math.min(1, 1 - mse / 0.5f));
        boolean confirmed = s.lich != null && s.lichErrors <= 2 && s.metric < -150;
        if (confirmed) { good++; adapt(chosen, before, s); collectLich(s); }
        listener.frame(s, quality);
        if (s.last()) end("last frame");
    }

    /** A packet frame. Voice frames never come here: this state is entered only from a packet-mode LSF. */
    private void packetFrame() {
        long p0 = next48; float[] sym = new float[192]; trackFrame(p0, sym);
        int pm = 0, em = 0;
        for (int i = 0; i < 8; i++) {
            int s = (int) slice(sym[i]);
            if (s == M17.SYMBOL_MAP[(M17.SYNC_PKT >> (14 - 2 * i)) & 3]) pm++;
            if (s == M17.EOT_SYMBOLS[i]) em++;
        }
        if (em >= 7 && em > pm) { end("end of transmission"); return; }
        if (pm < 6) { if (++lost >= 2) { end("signal lost"); return; } return; }
        lost = 0; frames++;
        M17.Packet p = M17.decodePacket(sym, 8);
        if (packets != null) packets.add(p);
        if (p.eof) {
            byte[] body = M17.assemble(packets);
            if (body != null) listener.packet(lsf, body); else listener.framesLost();
            end("last frame");
        }
    }

    /** NLMS on a confirmed frame: re-encode it, then nudge the coefficients toward the exact symbols. */
    private void adapt(long p0, float[] hist0, M17.Stream s) {
        if (adaptRate <= 0) return;
        int[] exact = M17.streamSymbolsFromLich(s.lich, s.fn, s.payload);
        float[] h = hist0.clone();
        for (int k = 0; k < 192; k++) {
            features(p0 + (long) k * SPS48, h);
            float y = out(), e = exact[k] - y, nrm = 1e-3f;
            for (float v : f) nrm += v * v;
            float mu = adaptRate * e / nrm;
            for (int i = 0; i < NCOEF; i++) coef[i] += mu * f[i];
            System.arraycopy(h, 0, h, 1, FB - 1); h[0] = exact[k];
        }
    }

    private void collectLich(M17.Stream s) {
        int c = s.lichCount(); if (c < 0 || c > 5) return;
        lichChunks[c] = java.util.Arrays.copyOf(s.lich, 5);
        if (lsfFromLichSent || (lsf != null && lsf.crcOk)) return;
        for (byte[] ch : lichChunks) if (ch == null) return;
        byte[] raw = new byte[30]; for (int i = 0; i < 6; i++) System.arraycopy(lichChunks[i], 0, raw, 5 * i, 5);
        M17.Lsf l = new M17.Lsf(raw, M17.crc16(raw, 28) == (((raw[28] & 0xFF) << 8) | (raw[29] & 0xFF)));
        if (l.crcOk) { lsfFromLichSent = true; lsf = l; listener.lsfFromLich(l); }
    }

    private void end(String why) { listener.ended(frames, good, why); reset(null); }
    private void reset(String why) { state = State.IDLE; strong = 0; preStart48 = preEnd48 = -1; }

    // ---- calibration (bench: a known transmission recorded through this radio) ---------------------------------

    /**
     * Least-squares fit of the coefficients on a recorded transmission whose symbols are known: {@code start8k} is roughly
     * where symbol 0 (the first preamble symbol) is in the recording; the exact delay is searched. Returns {coefficients..., mse}.
     */
    public static float[] calibrate(short[] audio, int start8k, int[] symbols) {
        M17Receiver r = new M17Receiver(new float[NCOEF], new Listener() {
            public void started(M17.Lsf l) { } public void frame(M17.Stream s, float q) { } public void lsfFromLich(M17.Lsf l) { } public void ended(int a, int b, String w) { } });
        int from = Math.max(0, start8k - 4000), to = Math.min(audio.length, start8k + symbols.length * 8000 / 4800 + 4000);
        r.feedRaw(java.util.Arrays.copyOfRange(audio, from, to));      // just this transmission: the ring holds 8 s
        long base = to48(start8k - from);
        double[] best = null; double bestE = Double.MAX_VALUE;
        for (int d = -120; d <= 120; d += 2) {
            double[] c = r.fit(base + d, symbols); if (c == null) continue;
            if (c[NCOEF] < bestE) { bestE = c[NCOEF]; best = c; }
        }
        float[] out = new float[NCOEF + 1]; for (int i = 0; i <= NCOEF; i++) out[i] = (float) best[i]; return out;
    }
    private void feedRaw(short[] a) { state = State.IDLE; for (short v : a) interpolate(v); }
    private double[] fit(long p0, int[] sym) {
        if (p0 + (long) sym.length * SPS48 + 5 * L >= n48 || n48 - p0 > RING) return null;
        double e = 0; int cnt = 0;
        for (long i = p0 + 20 * SPS48; i < p0 + 172 * SPS48; i++) { float v = at(i); e += v * v; cnt++; }
        gain = (float) Math.sqrt(e / cnt);
        double[][] A = new double[NCOEF][NCOEF]; double[] b = new double[NCOEF]; float[] h = new float[FB];
        for (int k = 0; k < sym.length; k++) {
            features(p0 + (long) k * SPS48, h);
            for (int i = 0; i < NCOEF; i++) { b[i] += f[i] * sym[k]; for (int j = i; j < NCOEF; j++) A[i][j] += f[i] * f[j]; }
            System.arraycopy(h, 0, h, 1, FB - 1); h[0] = sym[k];
        }
        for (int i = 0; i < NCOEF; i++) { A[i][i] += 1e-6 * sym.length; for (int j = 0; j < i; j++) A[i][j] = A[j][i]; }
        double[] c = solve(A, b); if (c == null) return null;
        double err = 0; java.util.Arrays.fill(h, 0);
        for (int k = 0; k < sym.length; k++) { features(p0 + (long) k * SPS48, h); double y = 0; for (int i = 0; i < NCOEF; i++) y += c[i] * f[i]; err += (y - sym[k]) * (y - sym[k]); System.arraycopy(h, 0, h, 1, FB - 1); h[0] = sym[k]; }
        double[] out = java.util.Arrays.copyOf(c, NCOEF + 1); out[NCOEF] = err / sym.length; return out;
    }
    private static double[] solve(double[][] A, double[] b) {
        int n = b.length; double[][] M = new double[n][]; for (int i = 0; i < n; i++) { M[i] = java.util.Arrays.copyOf(A[i], n + 1); M[i][n] = b[i]; }
        for (int c = 0; c < n; c++) {
            int p = c; for (int r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
            if (Math.abs(M[p][c]) < 1e-12) return null;
            double[] t = M[c]; M[c] = M[p]; M[p] = t;
            for (int r = c + 1; r < n; r++) { double k = M[r][c] / M[c][c]; if (k == 0) continue; for (int j = c; j <= n; j++) M[r][j] -= k * M[c][j]; }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) { double s = M[r][n]; for (int j = r + 1; j < n; j++) s -= M[r][j] * x[j]; x[r] = s / M[r][r]; }
        return x;
    }
}
