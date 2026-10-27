/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * POCSAG batches: BCH(31,21) codewords, 576-bit preamble, sync, and idle fill so each address
 * sits in the frame its low three bits name. Alphanumeric text is 7-bit, least-significant bit first.
 * One transmission can carry several addresses. A sender tag is this app's own convention at the
 * start of the text ({@code CALLSIGN: message}); a plain pager shows that as text.
 */
public final class Pocsag {
    private Pocsag() { }
    public static final int SYNC = 0x7CD215D8;
    public static final int IDLE = 0x7A89C197;
    private static final int G = 0x769;
    /** TX-RAM counts per baud. A positive count deviates low, so the 0 bit uses the negative value. The 512-baud step is smaller: a long run of one bit at 7600 reached 6281 Hz. */
    public static int level(int baud) {
        if (baud == 512) return 4600;    // 5400 still reached 5199 Hz on a half-second slice
        if (baud == 2400) return 7000;   // worst slice 4417 Hz
        return 6500;                     // 7000 reached 4814 Hz on the three-address page
    }

    public static final class Page {
        public final int ric, func;
        public final String text;
        public Page(int ric, int func, String text) {
            this.ric = ric; this.func = func & 3; this.text = text == null ? "" : text;
        }
        /** Bits repaired by BCH while this page was decoded. Zero for a page this app built to send. */
        public int corrected;
        /** The address word did not check. The text is kept and the number is not guessed. */
        public boolean ricUnreadable;
    }

    public static int bch(int data21) {
        long r = (data21 & 0x1FFFFF) << 10;
        for (int i = 30; i >= 10; i--) if ((r & (1L << i)) != 0) r ^= (long) G << (i - 10);
        return (int) (r & 0x3FF);
    }

    public static int codeword(int data21) {
        long cw = ((data21 & 0x1FFFFFL) << 10) | bch(data21);
        return (int) ((cw << 1) | (Long.bitCount(cw) & 1));
    }

    public static boolean check(int cw) {
        long data = (cw & 0xFFFFFFFFL) >>> 1;
        long r = data;
        for (int i = 30; i >= 10; i--) if ((r & (1L << i)) != 0) r ^= (long) G << (i - 10);
        return (r & 0x3FF) == 0 && (Long.bitCount(cw & 0xFFFFFFFFL) % 2 == 0);
    }

    /**
     * The codeword, or the same word with one or two bits of the BCH word repaired. The even-parity bit is checked
     * after that repair; if it does not match, the word is uncorrectable and comes back unchanged.
     */
    public static int correct(int cw) {
        if (check(cw)) return cw;
        int body = (int) ((cw & 0xFFFFFFFFL) >>> 1);
        int parity = cw & 1;
        int fixed = correctBody(body);
        if (fixed < 0 || parity != (Integer.bitCount(fixed) & 1)) return cw;
        return (fixed << 1) | parity;
    }

    /** One unique 31-bit word within two flips whose BCH syndrome is zero, or -1. */
    private static int correctBody(int body) {
        if (syndrome(body) == 0) return body & 0x7FFFFFFF;
        int one = searchFlips(body, 1, 0, 0, -1);
        if (one == -2) return -1;
        if (one >= 0) return one;
        int two = searchFlips(body, 2, 0, 0, -1);
        return two < 0 ? -1 : two;
    }

    private static int searchFlips(int body, int left, int from, int flipped, int found) {
        if (left == 0) return (syndrome(body ^ flipped) == 0) ? oneOf(found, body ^ flipped) : found;
        for (int i = from; i < 31 && found != -2; i++) found = searchFlips(body, left - 1, i + 1, flipped ^ (1 << i), found);
        return found;
    }

    private static int oneOf(int found, int word) {
        word &= 0x7FFFFFFF;
        if (found < 0) return word;
        return found == word ? found : -2;
    }

    private static int syndrome(int body) {
        long r = body & 0x7FFFFFFFL;
        for (int i = 30; i >= 10; i--) if ((r & (1L << i)) != 0) r ^= (long) G << (i - 10);
        return (int) (r & 0x3FF);
    }

    public static int addressWord(int ric, int func) {
        return codeword((((ric & 0x1FFFFF) >> 3) << 2) | (func & 3));
    }

    public static List<Integer> messageWords(String text) {
        List<Integer> bits = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            int v = text.charAt(i) & 0x7F;
            for (int b = 0; b < 7; b++) bits.add((v >> b) & 1);
        }
        while (bits.size() % 20 != 0) bits.add(0);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < bits.size(); i += 20) {
            int d = 0;
            for (int k = 0; k < 20; k++) d = (d << 1) | bits.get(i + k);
            out.add(codeword((1 << 20) | d));
        }
        return out;
    }

    /** Codewords of one transmission, idle-filled so each address lands in its frame. */
    public static List<Integer> words(List<Page> pages) {
        List<Integer> words = new ArrayList<>();
        for (Page p : pages) {
            if (p.ric < 0 || p.ric > 0x1FFFFF) throw new IllegalArgumentException("RIC out of range");
            int frame = p.ric & 7;
            while ((words.size() / 2) % 8 != frame) words.add(IDLE);
            words.add(addressWord(p.ric, p.func));
            words.addAll(messageWords(p.text));
        }
        return words;
    }

    /** Preamble, then sync + 16 codewords, repeated. Each bit is 0 or 1. */
    public static int[] bits(List<Page> pages) {
        List<Integer> words = words(pages);
        List<Integer> bits = new ArrayList<>();
        for (int i = 0; i < 576; i++) bits.add(1 - (i % 2));    // 1,0 repeated: 576-bit preamble
        int i = 0;
        do {
            bits.addAll(wordBits(SYNC));
            for (int k = 0; k < 16; k++) bits.addAll(wordBits(i + k < words.size() ? words.get(i + k) : IDLE));
            i += 16;
        } while (i < words.size());
        bits.addAll(wordBits(IDLE));                                 // one idle word after the last batch, so the drop does not clip it
        int[] a = new int[bits.size()];
        for (int n = 0; n < a.length; n++) a[n] = bits.get(n);
        return a;
    }

    private static List<Integer> wordBits(int cw) {
        List<Integer> b = new ArrayList<>(32);
        long u = cw & 0xFFFFFFFFL;
        for (int k = 31; k >= 0; k--) b.add((int) ((u >> k) & 1));
        return b;
    }

    /** Four 2-level symbols per byte, first symbol in bits 7:6. Symbol values are 0 and 1. */
    public static byte[] pack(int[] bits) {
        int n = (bits.length + 3) & ~3;
        byte[] o = new byte[n / 4];
        for (int i = 0; i < n; i += 4) {
            int b = 0;
            for (int k = 0; k < 4; k++) {
                int s = i + k < bits.length ? bits[i + k] & 1 : 0;
                b = (b << 2) | s;
            }
            o[i / 4] = (byte) b;
        }
        return o;
    }

    /** Op 8 body: hold shaping, two levels, the baud as a rational advance, amplitudes ±{@link #level(int)}. */
    public static byte[] waveform(int baud) {
        int num, den;
        if (baud == 512) { num = 8; den = 125; }
        else if (baud == 2400) { num = 3; den = 10; }
        else { num = 3; den = 20; baud = 1200; }
        byte[] b = new byte[11];
        b[0] = 8; b[1] = 0; b[2] = 2;
        b[3] = (byte) num; b[4] = (byte) (num >> 8);
        b[5] = (byte) den; b[6] = (byte) (den >> 8);
        int level = level(baud);
        put16(b, 7, -level); put16(b, 9, level);   // a positive count deviates low on this radio, so a 0 bit is the negative count and comes out positive
        return b;
    }

    private static void put16(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); }

    /** Seconds the bits take at this baud, preamble included. */
    public static double seconds(int[] bits, int baud) {
        int rate = baud == 512 || baud == 2400 ? baud : 1200;
        return bits.length / (double) rate;
    }

    /** {@code CALLSIGN: message}. A blank callsign leaves the text unchanged. */
    public static String sign(String callsign, String text) {
        String c = callsign == null ? "" : callsign.trim().toUpperCase(Locale.US);
        int d = c.indexOf('-'); if (d > 0) c = c.substring(0, d);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length() && sb.length() < 9; i++) {
            char ch = c.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) sb.append(ch);
        }
        if (sb.length() == 0) return text == null ? "" : text;
        return sb + ": " + (text == null ? "" : text);
    }

    /** The callsign before {@code ": "}, or null when the text has no sender tag. */
    public static String sender(String text) {
        if (text == null) return null;
        int c = text.indexOf(": ");
        if (c < 1 || c > 9) return null;
        String t = text.substring(0, c);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (!((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))) return null;
        }
        return t;
    }

    public static String body(String text) {
        String s = sender(text);
        return s == null ? (text == null ? "" : text) : text.substring(s.length() + 2);
    }

    /** Pages recovered from a bit string. Codewords that fail BCH are skipped. */
    public static List<Page> decode(int[] bits) {
        List<Page> out = new ArrayList<>();
        Page cur = null;
        StringBuilder text = new StringBuilder();
        List<Integer> leftover = new ArrayList<>();
        int i = 0;
        while (i + 32 <= bits.length) {
            int w = wordAt(bits, i);
            if (w != SYNC) { i++; continue; }
            for (int k = 0; k < 16; k++) {
                int at = i + 32 + 32 * k;
                if (at + 32 > bits.length) break;
                int raw = wordAt(bits, at);
                int cw = correct(raw);
                if (cw == IDLE) { finish(out, cur, text, leftover); cur = null; text.setLength(0); leftover.clear(); continue; }
                if (!check(cw)) { finish(out, cur, text, leftover); cur = null; text.setLength(0); leftover.clear(); continue; }
                if (cur != null) cur.corrected += Integer.bitCount(raw ^ cw);
                int data = (int) ((cw & 0xFFFFFFFFL) >>> 11);
                if ((data & (1 << 20)) == 0) {
                    finish(out, cur, text, leftover);
                    int frame = k / 2;
                    int ric = ((data >> 2) & 0x3FFFF) << 3 | frame;
                    cur = new Page(ric, data & 3, "");
                    cur.corrected = Integer.bitCount(raw ^ cw);
                    text.setLength(0); leftover.clear();
                } else if (cur != null) {
                    int payload = data & 0xFFFFF;
                    int width = cur.func == 0 ? 4 : 7;
                    for (int b = 19; b >= 0; b--) leftover.add((payload >> b) & 1);
                    while (leftover.size() >= width) {
                        int v = 0;
                        for (int b = 0; b < width; b++) v |= leftover.remove(0) << b;
                        if (cur.func == 0) { if (v < 16 && "0123456789*U -)(".charAt(v) != ' ') text.append("0123456789*U -)(".charAt(v)); }
                        else if (v != 0) text.append((char) v);
                    }
                }
            }
            i += 32 * 17;   // a message may continue in the next batch
        }
        finish(out, cur, text, leftover);
        return out;
    }

    private static void finish(List<Page> out, Page cur, StringBuilder text, List<Integer> leftover) {
        if (cur == null || text.length() == 0) return;
        Page p = new Page(cur.ricUnreadable ? -1 : cur.ric, cur.func, text.toString());
        p.corrected = cur.corrected;
        p.ricUnreadable = cur.ricUnreadable;
        out.add(p);
    }

    private static int wordAt(int[] bits, int at) {
        int w = 0;
        for (int k = 0; k < 32; k++) w = (w << 1) | (bits[at + k] & 1);
        return w;
    }
}
