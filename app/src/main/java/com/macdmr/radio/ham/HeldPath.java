/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.ArrayList;
import java.util.List;

/**
 * Held-path M17 front end. The phone's voice filter is held open in the module, and a fixed 41-tap equalizer
 * (one phone model) runs on the matched-filter output before the sync search. The frame decoder is {@link M17}.
 * The plain path keeps {@link M17Receiver}.
 *
 * The filter matches the R-F measurement: taps at symbol spacing, sync threshold 0.95, then the eight sync
 * symbols set the gain. Index 0 of the taps is the coefficient 20 symbols ahead.
 */
public final class HeldPath {
    public static final int TAPS = 41;
    static final int L = 20, SPS = 10;
    /** 8 kHz samples of past context the live search keeps before its resume point (the equaliser's 20 symbols either side, the pulse span, and room). */
    static final int PAST = 400;
    static final double THRESH = 0.95, LOST = 0.6;
    private static final double[] RRC = rrc(0.5, 8, SPS);
    private static final double[] SYNC_LSF = sync(M17.SYNC_LSF), SYNC_STR = sync(M17.SYNC_STR);
    private static final double SS_LSF = energy(SYNC_LSF), SS_STR = energy(SYNC_STR);

    private HeldPath() { }

    /** One locked frame. {@code link} is set for a link setup, {@code stream} for a stream frame. */
    public static final class FrameHit {
        public final int syncAt;
        public final float score;
        public final M17.Lsf link;
        public final M17.Stream stream;
        FrameHit(int at, float score, M17.Lsf link, M17.Stream stream) {
            syncAt = at; this.score = score; this.link = link; this.stream = stream;
        }
        public boolean lsf() { return link != null; }
    }

    /** 8 kHz mono, with the bench clip's quiet head trimmed. Returns the frames in order. */
    public static List<FrameHit> decode(short[] pcm, int n, double[] taps) { return decode(pcm, n, taps, true); }

    public static List<FrameHit> decode(short[] pcm, double[] taps) { return decode(pcm, pcm.length, taps, true); }

    /**
     * 8 kHz mono. {@code trimHead} skips the quiet lead and the first quarter-second of audio, which the saved
     * bench clips need. Live audio leaves that out: a real preamble is only 40 ms, and the trim would drop the link setup.
     */
    public static List<FrameHit> decode(short[] pcm, int n, double[] taps, boolean trimHead) {
        if (taps == null || taps.length != TAPS) throw new IllegalArgumentException("taps");
        double[] x = trimHead ? loud(pcm, n) : signed(pcm, n);
        if (x.length < 800) return new ArrayList<>();
        double[] y = equalise(matched(resample6(x)), taps);
        return frames(y);
    }

    static double[] signed(short[] pcm, int n) {
        if (n <= 0) return new double[0];
        double mean = 0; for (int i = 0; i < n; i++) mean += pcm[i]; mean /= n;
        double[] o = new double[n];
        for (int i = 0; i < n; i++) o[i] = -(pcm[i] - mean);
        return o;
    }

    /** First sample of the trimmed signal: the squelch-open window, plus a quarter second. */
    static int loudStart(short[] pcm, int n) {
        for (int i = 0; i + 400 <= n; i += 80) {
            double m = 0; for (int k = 0; k < 400; k++) m += pcm[i + k]; m /= 400;
            double e = 0; int quiet = 0;
            for (int k = 0; k < 400; k++) {
                double d = pcm[i + k] - m; e += d * d;
                if (Math.abs(pcm[i + k]) <= MUTE_PEAK) quiet++;
            }
            if (quiet <= 40 && Math.sqrt(e / 400) > 800) return Math.min(n, i + 2000);
        }
        return 0;
    }

    static double[] loud(short[] pcm, int n) {
        int i0 = loudStart(pcm, n);
        if (i0 >= n) return new double[0];
        return signed(java.util.Arrays.copyOfRange(pcm, i0, n), n - i0);
    }

    /** Linear upsample 8 kHz to 48 kHz, six samples per input sample. Past the last input sample the value holds. */
    static double[] resample6(double[] x) {
        int n48 = x.length * 6;
        double[] y = new double[n48];
        for (int i = 0; i < n48; i++) {
            double t = i / 6.0;
            int a = (int) t;
            double f = t - a;
            double va = x[Math.min(a, x.length - 1)];
            double vb = x[Math.min(a + 1, x.length - 1)];
            y[i] = va + (vb - va) * f;
        }
        return y;
    }

    static double[] matched(double[] x) {
        int c = RRC.length / 2, n = x.length;
        double[] o = new double[n];
        for (int i = 0; i < n; i++) {
            int k = i + c; double s = 0;
            int jLo = Math.max(0, k - (n - 1)), jHi = Math.min(RRC.length - 1, k);
            for (int j = jLo; j <= jHi; j++) s += RRC[j] * x[k - j];
            o[i] = s;
        }
        return o;
    }

    static double[] equalise(double[] y, double[] w) {
        int n = y.length;
        double[] ye = new double[n];
        for (int i = 0; i < w.length; i++) {
            int sh = (L - i) * SPS;
            double t = w[i];
            if (sh >= 0) { for (int k = 0; k < n - sh; k++) ye[k] += t * y[k + sh]; }
            else { int s = -sh; for (int k = s; k < n; k++) ye[k] += t * y[k - s]; }
        }
        return ye;
    }

    private static List<FrameHit> frames(double[] y) {
        List<FrameHit> out = new ArrayList<>();
        int limit = y.length - 8 * SPS;
        if (limit <= 0) return out;
        int bestB = -1; boolean bestLsf = true;
        for (int kind = 0; kind < 2; kind++) {
            double[] s = kind == 0 ? SYNC_LSF : SYNC_STR; double ss = kind == 0 ? SS_LSF : SS_STR;
            int a = -1;
            for (int i = 0; i < limit; i++) if (score(y, i, s, ss) > THRESH) { a = i; break; }
            if (a < 0) continue;
            int b = a; double sb = -1;
            int hi = Math.min(limit, a + SPS);
            for (int i = a; i < hi; i++) { double sc = score(y, i, s, ss); if (sc > sb) { sb = sc; b = i; } }
            if (bestB < 0 || b < bestB) { bestB = b; bestLsf = kind == 0; }
        }
        if (bestB < 0) return out;
        int pos = bestB; boolean lsf = bestLsf;
        while (pos + 192 * SPS <= y.length + 8 * SPS) {
            double sc = score(y, pos, lsf ? SYNC_LSF : SYNC_STR, lsf ? SS_LSF : SS_STR);
            int start = pos + 8 * SPS;
            float[] pay = payload(y, start, lsf ? SYNC_LSF : SYNC_STR);
            if (pay == null) break;
            if (lsf) out.add(new FrameHit(pos, (float) sc, M17.decodeLsf(pay, 0), null));
            else out.add(new FrameHit(pos, (float) sc, null, M17.decodeStream(pay, 0)));
            int nxt = pos + 192 * SPS;
            double best = -1; int bp = nxt; boolean bl = false;
            for (int d = -(SPS / 2); d <= SPS / 2; d++) {
                for (int kind = 0; kind < 2; kind++) {
                    boolean useLsf = kind == 1;
                    double[] s = useLsf ? SYNC_LSF : SYNC_STR; double ss = useLsf ? SS_LSF : SS_STR;
                    double c = score(y, nxt + d, s, ss);
                    if (c > best) { best = c; bp = nxt + d; bl = useLsf; }
                }
            }
            if (best < LOST) break;
            pos = bp; lsf = bl;
        }
        return out;
    }

    /** 184 payload symbols, gain and offset taken from the eight sync symbols. Null when the frame runs off the end. */
    private static float[] payload(double[] y, int start, double[] sync) {
        int syncAt = start - 8 * SPS;
        if (syncAt < 0 || start + 183 * SPS >= y.length) return null;
        double ss = 0, sy = 0, ssy = 0, yy = 0;
        for (int i = 0; i < 8; i++) {
            double s = sync[i], v = y[syncAt + i * SPS];
            ss += s * s; sy += s; ssy += s * v; yy += v;
        }
        double det = ss * 8 - sy * sy;
        if (Math.abs(det) < 1e-9) return null;
        double g = (8 * ssy - sy * yy) / det, off = (ss * yy - sy * ssy) / det;
        if (Math.abs(g) < 1e-6) return null;
        float[] pay = new float[184];
        for (int i = 0; i < 184; i++) pay[i] = (float) ((y[start + i * SPS] - off) / g);
        return pay;
    }

    private static double score(double[] y, int i, double[] s, double ss) {
        if (i < 0 || i + 7 * SPS >= y.length) return -1;
        double dot = 0, vv = 0;
        for (int k = 0; k < 8; k++) { double v = y[i + k * SPS]; dot += v * s[k]; vv += v * v; }
        return dot / (Math.sqrt(ss * vv) + 1e-12);
    }

    private static double[] sync(int word) {
        double[] s = new double[8];
        for (int i = 0; i < 8; i++) s[i] = M17.SYMBOL_MAP[(word >> (14 - 2 * i)) & 3];
        return s;
    }
    private static double energy(double[] s) { double e = 0; for (double v : s) e += v * v; return e; }

    static double[] rrc(double alpha, int span, int sps) {
        int n = span * sps + 1;
        double[] h = new double[n];
        double e = 0;
        for (int i = 0; i < n; i++) {
            double x = (i - span * sps / 2.0) / sps;
            double v;
            if (Math.abs(x) < 1e-9) v = 1 - alpha + 4 * alpha / Math.PI;
            else if (Math.abs(Math.abs(4 * alpha * x) - 1) < 1e-9)
                v = alpha / Math.sqrt(2) * ((1 + 2 / Math.PI) * Math.sin(Math.PI / (4 * alpha)) + (1 - 2 / Math.PI) * Math.cos(Math.PI / (4 * alpha)));
            else v = (Math.sin(Math.PI * x * (1 - alpha)) + 4 * alpha * x * Math.cos(Math.PI * x * (1 + alpha))) / (Math.PI * x * (1 - (4 * alpha * x) * (4 * alpha * x)));
            h[i] = v; e += v * v;
        }
        double s = Math.sqrt(e);
        for (int i = 0; i < n; i++) h[i] /= s;
        return h;
    }

    /** Unequalised symbol-rate samples around one sync: 20 symbols of context, the 192-symbol frame, 20 more. {@code syncAt48} is the sync's sample in the 48 kHz matched-filter output of {@code pcm} from {@code origin8}. */
    static double[] rawWindow(short[] pcm, int origin8, int syncAt48) {
        if (pcm == null || origin8 < 0 || origin8 >= pcm.length) return null;
        int n = pcm.length - origin8;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = -pcm[origin8 + i];
        double[] m = matched(resample6(x));
        int lo = syncAt48 - L * SPS;
        double[] w = new double[HeldFit.L + HeldFit.FRAME + HeldFit.L];
        for (int i = 0; i < w.length; i++) {
            int at = lo + i * SPS;
            w[i] = at >= 0 && at < m.length ? m[at] : 0;
        }
        return w;
    }

    /** The root-raised-cosine centre tap, for the host check against the research filter. */
    static double rrcCentre() { return RRC[RRC.length / 2]; }

    /** Raw PCM at or under this peak is the module holding the speaker mute (about 5 counts). Sync is not searched there. */
    static final int MUTE_PEAK = 20;

    /** Every sync chain in {@code pcm}, starting at 8 kHz sample {@code from}. {@code resume8} is where the next pass should start. */
    static final class Live {
        final List<List<FrameHit>> chains;
        final int resume8;
        /** The 8 kHz sample of {@code pcm} the hits' 48 kHz positions count from (the muted head skipped). */
        final int origin8;
        /** The walk stopped because the next sync fell below the hold threshold, not because the buffer ran out. */
        final boolean closed;
        Live(List<List<FrameHit>> chains, int resume8, boolean closed) { this(chains, resume8, closed, 0); }
        Live(List<List<FrameHit>> chains, int resume8, boolean closed, int origin8) {
            this.chains = chains; this.resume8 = resume8; this.closed = closed; this.origin8 = origin8;
        }
    }

    static Live live(short[] pcm, int n, double[] taps, int from) { return live(pcm, n, taps, from, false); }

    /**
     * {@code inCall}: the receiver is already following a transmission, so a chain found here is part of it even
     * before its link setup or six LICH chunks have been seen again, and the filters get {@link #PAST} samples of
     * context before {@code from} instead of a cold start at the cut.
     */
    static Live live(short[] pcm, int n, double[] taps, int from, boolean inCall) {
        if (taps == null || taps.length != TAPS) throw new IllegalArgumentException("taps");
        if (from < 0) from = 0;
        if (from >= n) return new Live(new ArrayList<List<FrameHit>>(), n, false);
        if (n - from < 800) return new Live(new ArrayList<List<FrameHit>>(), from, false);   // too little new audio to search: keep it for the next pass (a pass that returned n here threw away up to 100 ms per poll)
        // The saved clips, and a transmission with the 1.6 s lead, have a loud head before the link
        // setup. Skip it only while the resume point is still in the mute. Once audio is loud, trimming
        // again would drop the next frames.
        int origin = from;
        boolean mute = true;
        for (int i = from; i < Math.min(n, from + 80); i++) if (Math.abs(pcm[i]) > MUTE_PEAK) { mute = false; break; }
        if (mute) {
            int loud = loudStart(pcm, n);
            if (loud > origin) origin = loud;
        }
        if (origin >= n) return new Live(new ArrayList<List<FrameHit>>(), n, false);
        // Past context for the matched filter and the equaliser (20 symbols either side): the search itself
        // still starts at the resume point, so frames before it are not found twice.
        int pad = mute ? 0 : Math.min(origin, PAST);
        origin -= pad;
        short[] part = java.util.Arrays.copyOfRange(pcm, origin, n);
        // No per-slice mean: a poll that sees only part of the transmission would shift the level and
        // drop sync in the middle of a call. The sign matches the bench clips.
        double[] x = new double[part.length];
        for (int i = 0; i < part.length; i++) x[i] = -part[i];
        double[] y = equalise(matched(resample6(x)), taps);
        List<List<FrameHit>> chains = new ArrayList<List<FrameHit>>();
        int limit = y.length - 8 * SPS;
        int from48 = pad * 6;
        int resume = n;
        boolean closed = false;
        while (from48 < limit) {
            int[] at = firstSync(y, part, part.length, from48, limit);
            if (at == null) {
                resume = chains.isEmpty() ? n : Math.max(origin, origin + from48 / 6 - 2);
                break;
            }
            int pos = at[0]; boolean lsf = at[1] == 0;
            List<FrameHit> chain = new ArrayList<FrameHit>();
            int end48 = pos;
            boolean abandoned = false, needMore = false, lost = false;
            while (pos + 192 * SPS <= y.length + 8 * SPS) {
                int start = pos + 8 * SPS;
                float[] pay = payload(y, start, lsf ? SYNC_LSF : SYNC_STR);
                if (pay == null) { needMore = true; break; }
                double sc = score(y, pos, lsf ? SYNC_LSF : SYNC_STR, lsf ? SS_LSF : SS_STR);
                if (lsf) chain.add(new FrameHit(pos, (float) sc, M17.decodeLsf(pay, 0), null));
                else chain.add(new FrameHit(pos, (float) sc, null, M17.decodeStream(pay, 0)));
                if (chain.size() >= 8 && !inCall && !identified(chain)) { abandoned = true; chain.clear(); break; }
                end48 = pos + 192 * SPS;
                int nxt = end48;
                double best = -1; int bp = nxt; boolean bl = false;
                for (int d = -(SPS / 2); d <= SPS / 2; d++) {
                    if (!loudAt(part, part.length, nxt + d)) continue;
                    for (int kind = 0; kind < 2; kind++) {
                        boolean useLsf = kind == 1;
                        double c = score(y, nxt + d, useLsf ? SYNC_LSF : SYNC_STR, useLsf ? SS_LSF : SS_STR);
                        if (c > best) { best = c; bp = nxt + d; bl = useLsf; }
                    }
                }
                if (best < LOST) {
                    // The receiver polls while audio is still arriving. A sync within a few frames of the
                    // end of this slice has not ended the transmission; the rest of the frame is not in yet.
                    if (nxt + 3 * 192 * SPS > y.length) needMore = true;
                    else lost = true;
                    break;
                }
                pos = bp; lsf = bl;
            }
            if (!chain.isEmpty()) chains.add(chain);
            closed = lost && !abandoned && !needMore;
            if (needMore) {
                // The next frame runs off the buffer. Keep what decoded and retry this sync when more audio arrives.
                int hold = chain.isEmpty() ? at[0] : end48;
                resume = Math.max(origin, origin + Math.max(0, hold) / 6 - 2);
                break;
            }
            from48 = abandoned ? at[0] + 1 : Math.max(end48, at[0] + SPS);
            resume = origin + from48 / 6;
            // A false sync before the transmission is unidentified and the search continues. Once a real
            // transmission loses sync, the rest of this buffer is not more of that contact.
            if (closed && (inCall || identified(chain))) { resume = n; break; }
        }
        return new Live(chains, resume, closed, origin);
    }

    /** First sync at or after {@code from48}. Returns {index, 0 lsf / 1 stream}, or null. */
    private static int[] firstSync(double[] y, short[] pcm, int n, int from48, int limit) {
        int bestB = -1; int bestKind = 0;
        for (int kind = 0; kind < 2; kind++) {
            double[] s = kind == 0 ? SYNC_LSF : SYNC_STR; double ss = kind == 0 ? SS_LSF : SS_STR;
            int a = -1;
            for (int i = Math.max(0, from48); i < limit; i++) {
                if (!loudAt(pcm, n, i)) continue;
                if (score(y, i, s, ss) > THRESH) { a = i; break; }
            }
            if (a < 0) continue;
            int b = a; double sb = -1;
            int hi = Math.min(limit, a + SPS);
            for (int i = a; i < hi; i++) {
                if (!loudAt(pcm, n, i)) continue;
                double sc = score(y, i, s, ss);
                if (sc > sb) { sb = sc; b = i; }
            }
            if (bestB < 0 || b < bestB) { bestB = b; bestKind = kind; }
        }
        return bestB < 0 ? null : new int[] { bestB, bestKind };
    }

    /** A real transmission shows a link-setup CRC, or six LICH chunks, within the first frames. */
    private static boolean identified(List<FrameHit> chain) {
        byte[][] part = new byte[6][];
        for (FrameHit h : chain) {
            if (h.link != null && h.link.crcOk) return true;
            if (h.stream == null || h.stream.lich == null) continue;
            int c = h.stream.lichCount();
            if (c >= 0 && c <= 5) part[c] = h.stream.lich;
        }
        for (byte[] p : part) if (p == null) return false;
        byte[] raw = new byte[30];
        for (int i = 0; i < 6; i++) System.arraycopy(part[i], 0, raw, 5 * i, 5);
        return M17.crc16(raw, 28) == (((raw[28] & 0xFF) << 8) | (raw[29] & 0xFF));
    }

    private static boolean loudAt(short[] pcm, int n, int i48) {
        if (i48 < 0) return false;
        int a = i48 / 6, b = Math.min(n - 1, (i48 + 8 * SPS) / 6);
        for (int i = Math.max(0, a); i <= b; i++) if (Math.abs(pcm[i]) > MUTE_PEAK) return true;
        return false;
    }
}
