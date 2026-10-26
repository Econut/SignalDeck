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
    /** TX-RAM counts for the two levels. FW21 at this amplitude, 1200 baud, measured body p99 4061 Hz. */
    public static final int LEVEL = 7600;

    public static final class Page {
        public final int ric, func;
        public final String text;
        public Page(int ric, int func, String text) {
            this.ric = ric; this.func = func & 3; this.text = text == null ? "" : text;
        }
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

    /** Op 8 body: hold shaping, two levels, the baud as a rational advance, amplitudes ±{@link #LEVEL}. */
    public static byte[] waveform(int baud) {
        int num, den;
        if (baud == 512) { num = 8; den = 125; }
        else if (baud == 2400) { num = 3; den = 10; }
        else { num = 3; den = 20; baud = 1200; }
        byte[] b = new byte[11];
        b[0] = 8; b[1] = 0; b[2] = 2;
        b[3] = (byte) num; b[4] = (byte) (num >> 8);
        b[5] = (byte) den; b[6] = (byte) (den >> 8);
        put16(b, 7, LEVEL); put16(b, 9, -LEVEL);
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
                int cw = wordAt(bits, at);
                if (!check(cw) || cw == IDLE) { finish(out, cur, text, leftover); cur = null; text.setLength(0); leftover.clear(); continue; }
                int data = (int) ((cw & 0xFFFFFFFFL) >>> 11);
                if ((data & (1 << 20)) == 0) {
                    finish(out, cur, text, leftover);
                    int frame = k / 2;
                    int ric = ((data >> 2) & 0x3FFFF) << 3 | frame;
                    cur = new Page(ric, data & 3, "");
                    text.setLength(0); leftover.clear();
                } else if (cur != null) {
                    int payload = data & 0xFFFFF;
                    for (int b = 19; b >= 0; b--) leftover.add((payload >> b) & 1);
                    while (leftover.size() >= 7) {
                        int v = 0;
                        for (int b = 0; b < 7; b++) v |= leftover.remove(0) << b;
                        if (v != 0) text.append((char) v);
                    }
                }
            }
            i += 32 * 17;   // a message may continue in the next batch
        }
        finish(out, cur, text, leftover);
        return out;
    }

    private static void finish(List<Page> out, Page cur, StringBuilder text, List<Integer> leftover) {
        if (cur == null) return;
        out.add(new Page(cur.ric, cur.func, text.toString()));
    }

    private static int wordAt(int[] bits, int at) {
        int w = 0;
        for (int k = 0; k < 32; k++) w = (w << 1) | (bits[at + k] & 1);
        return w;
    }
}
