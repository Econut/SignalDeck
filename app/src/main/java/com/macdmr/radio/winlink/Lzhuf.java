/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * LZHUF, the compression of FBB B2 / Winlink messages: Yoshizaki and Okumura's LZSS with adaptive Huffman coding
 * (window 2048, look-ahead 60, threshold 2), the window pre-filled with spaces. The B2 form starts with a CRC-16
 * (XMODEM table, two zero bytes appended) of everything after it, then the uncompressed length (32-bit little
 * endian), then the bit stream. The output must match other Winlink programs byte for byte, so the algorithm is
 * kept exactly as the original (tree updates, tie-breaks, the final partial byte).
 */
public final class Lzhuf {
    private static final int N = 2048, F = 60, THRESHOLD = 2, NIL = N;
    private static final int N_CHAR = 256 - THRESHOLD + F, T = N_CHAR * 2 - 1, R = T - 1, MAX_FREQ = 0x8000;

    /** Position code tables: the upper 6 bits of a match position as a 3..8-bit prefix code. */
    private static final int[] P_LEN = new int[64], P_CODE = new int[64], D_CODE = new int[256], D_LEN = new int[256];
    private static final int[] CRC_TAB = new int[256];
    static {
        int[] counts = { 1, 3, 8, 12, 24, 16 };                  // codes of 3, 4, 5, 6, 7 and 8 bits
        int idx = 0, code = 0;
        for (int l = 0; l < counts.length; l++) {
            int len = l + 3;
            for (int k = 0; k < counts[l]; k++, idx++) {
                P_LEN[idx] = len; P_CODE[idx] = code;
                for (int b = 0; b < (1 << (8 - len)); b++) { D_CODE[code + b] = idx; D_LEN[code + b] = len; }
                code += 1 << (8 - len);
            }
        }
        for (int i = 0; i < 256; i++) {
            int c = i << 8;
            for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? (c << 1) ^ 0x1021 : c << 1;
            CRC_TAB[i] = c & 0xFFFF;
        }
    }

    // ---- public API ----------------------------------------------------------------------------------

    /** Compress in the B2 form (CRC + length + data). */
    public static byte[] compressB2(byte[] in) {
        byte[] body = compress(in);
        int crc = crc(body, 0, body.length);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) crc; out[1] = (byte) (crc >> 8);
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    /** Decompress the B2 form; throws when the checksum or the length does not match. */
    public static byte[] decompressB2(byte[] in) throws IOException {
        if (in.length < 6) throw new IOException("lzhuf: too short");
        int want = (in[0] & 0xFF) | ((in[1] & 0xFF) << 8);
        if (crc(in, 2, in.length - 2) != want) throw new IOException("lzhuf: checksum mismatch");
        byte[] body = new byte[in.length - 2]; System.arraycopy(in, 2, body, 0, body.length);
        return decompress(body);
    }

    /** The FBB CRC-16 of a block (as used by the B2 header). */
    static int crc(byte[] b, int off, int len) {
        int sum = 0;
        for (int i = 0; i < len; i++) sum = upd(b[off + i] & 0xFF, sum);
        sum = upd(0, sum); sum = upd(0, sum);
        return sum;
    }
    private static int upd(int c, int sum) { return ((sum << 8) & 0xFF00) ^ CRC_TAB[(sum >> 8) & 0xFF] ^ c; }

    // ---- shared Huffman state ---------------------------------------------------------------------------

    private final int[] freq = new int[T + 1], prnt = new int[T + N_CHAR], son = new int[T];
    private final int[] dad = new int[N + 1], lson = new int[N + 1], rson = new int[N + 257];
    private final int[] text = new int[N + F - 1];
    private int matchPosition, matchLength;

    private Lzhuf() { startHuff(); }

    private void startHuff() {
        for (int i = 0; i < N_CHAR; i++) { freq[i] = 1; son[i] = i + T; prnt[i + T] = i; }
        int i = 0, j = N_CHAR;
        while (j <= R) { freq[j] = freq[i] + freq[i + 1]; son[j] = i; prnt[i] = prnt[i + 1] = j; i += 2; j++; }
        freq[T] = 0xFFFF; prnt[R] = 0;
    }

    private void reconst() {
        int j = 0;
        for (int i = 0; i < T; i++) if (son[i] >= T) { freq[j] = (freq[i] + 1) / 2; son[j] = son[i]; j++; }
        for (int i = 0, jj = N_CHAR; jj < T; i += 2, jj++) {
            int f = freq[jj] = freq[i] + freq[i + 1];
            int k = jj - 1;
            while (f < freq[k]) k--;
            k++;
            System.arraycopy(freq, k, freq, k + 1, jj - k); freq[k] = f;
            System.arraycopy(son, k, son, k + 1, jj - k); son[k] = i;
        }
        for (int i = 0; i < T; i++) { int k = son[i]; if (k >= T) prnt[k] = i; else prnt[k] = prnt[k + 1] = i; }
    }

    private void update(int c) {
        if (freq[R] == MAX_FREQ) reconst();
        c = prnt[c + T];
        do {
            int k = ++freq[c];
            int l = c + 1;
            if (k > freq[l]) {
                while (k > freq[++l]) { }
                l--;
                freq[c] = freq[l]; freq[l] = k;
                int i = son[c]; prnt[i] = l; if (i < T) prnt[i + 1] = l;
                int j = son[l]; son[l] = i;
                prnt[j] = c; if (j < T) prnt[j + 1] = c;
                son[c] = j;
                c = l;
            }
        } while ((c = prnt[c]) != 0);
    }

    // ---- encoder ------------------------------------------------------------------------------------------

    private ByteArrayOutputStream out; private int putbuf, putlen;

    private void initTree() { for (int i = N + 1; i <= N + 256; i++) rson[i] = NIL; for (int i = 0; i < N; i++) dad[i] = NIL; }

    private void insertNode(int r) {
        int cmp = 1, p = N + 1 + text[r];
        rson[r] = lson[r] = NIL; matchLength = 0;
        for (;;) {
            if (cmp >= 0) { if (rson[p] != NIL) p = rson[p]; else { rson[p] = r; dad[r] = p; return; } }
            else { if (lson[p] != NIL) p = lson[p]; else { lson[p] = r; dad[r] = p; return; } }
            int i;
            for (i = 1; i < F; i++) if ((cmp = text[r + i] - text[p + i]) != 0) break;
            if (i > THRESHOLD) {
                if (i > matchLength) { matchPosition = ((r - p) & (N - 1)) - 1; if ((matchLength = i) >= F) break; }
                if (i == matchLength) { int c = ((r - p) & (N - 1)) - 1; if (c < matchPosition) matchPosition = c; }
            }
        }
        dad[r] = dad[p]; lson[r] = lson[p]; rson[r] = rson[p];
        dad[lson[p]] = r; dad[rson[p]] = r;
        if (rson[dad[p]] == p) rson[dad[p]] = r; else lson[dad[p]] = r;
        dad[p] = NIL;
    }

    private void deleteNode(int p) {
        if (dad[p] == NIL) return;
        int q;
        if (rson[p] == NIL) q = lson[p];
        else if (lson[p] == NIL) q = rson[p];
        else {
            q = lson[p];
            if (rson[q] != NIL) {
                do { q = rson[q]; } while (rson[q] != NIL);
                rson[dad[q]] = lson[q]; dad[lson[q]] = dad[q];
                lson[q] = lson[p]; dad[lson[p]] = q;
            }
            rson[q] = rson[p]; dad[rson[p]] = q;
        }
        dad[q] = dad[p];
        if (rson[dad[p]] == p) rson[dad[p]] = q; else lson[dad[p]] = q;
        dad[p] = NIL;
    }

    private void putcode(int l, int c) {                    // c: the code left-aligned in 16 bits
        putbuf = (putbuf | (c >>> putlen)) & 0xFFFF;
        if ((putlen += l) >= 8) {
            out.write(putbuf >>> 8);
            if ((putlen -= 8) >= 8) { out.write(putbuf & 0xFF); putlen -= 8; putbuf = (c << (l - putlen)) & 0xFFFF; }
            else putbuf = (putbuf << 8) & 0xFFFF;
        }
    }

    private void encodeChar(int c) {
        int i = 0, j = 0, k = prnt[c + T];
        do { i >>>= 1; if ((k & 1) != 0) i += 0x8000; j++; } while ((k = prnt[k]) != R);
        putcode(j, i);
        update(c);
    }

    private void encodePosition(int c) {
        int i = c >> 6;
        putcode(P_LEN[i], P_CODE[i] << 8);
        putcode(6, (c & 0x3F) << 10);
    }

    /** Length (32-bit LE) + bit stream, without the B2 CRC. */
    static byte[] compress(byte[] in) {
        Lzhuf z = new Lzhuf();
        ByteArrayOutputStream o = new ByteArrayOutputStream(in.length / 2 + 16);
        o.write(in.length); o.write(in.length >> 8); o.write(in.length >> 16); o.write(in.length >> 24);
        if (in.length == 0) return o.toByteArray();
        z.out = o; z.initTree();
        int s = 0, r = N - F, pos = 0, len;
        for (int i = s; i < r; i++) z.text[i] = ' ';
        for (len = 0; len < F && pos < in.length; len++) z.text[r + len] = in[pos++] & 0xFF;
        for (int i = 1; i <= F; i++) z.insertNode(r - i);
        z.insertNode(r);
        do {
            if (z.matchLength > len) z.matchLength = len;
            if (z.matchLength <= THRESHOLD) { z.matchLength = 1; z.encodeChar(z.text[r]); }
            else { z.encodeChar(255 - THRESHOLD + z.matchLength); z.encodePosition(z.matchPosition); }
            int last = z.matchLength, i;
            for (i = 0; i < last && pos < in.length; i++) {
                int c = in[pos++] & 0xFF;
                z.deleteNode(s); z.text[s] = c; if (s < F - 1) z.text[s + N] = c;
                s = (s + 1) & (N - 1); r = (r + 1) & (N - 1);
                z.insertNode(r);
            }
            while (i++ < last) {
                z.deleteNode(s);
                s = (s + 1) & (N - 1); r = (r + 1) & (N - 1);
                if (--len != 0) z.insertNode(r);
            }
        } while (len > 0);
        if (z.putlen != 0) o.write(z.putbuf >>> 8);
        return o.toByteArray();
    }

    // ---- decoder ------------------------------------------------------------------------------------------

    private byte[] src; private int srcPos, getbuf, getlen;

    private void fill() { while (getlen <= 8) { int b = srcPos < src.length ? src[srcPos] & 0xFF : 0; srcPos++; getbuf |= b << (8 - getlen); getlen += 8; } }
    private int getBit() { fill(); int i = getbuf; getbuf = (getbuf << 1) & 0xFFFF; getlen--; return (i >> 15) & 1; }
    private int getByte() { fill(); int i = getbuf; getbuf = (getbuf << 8) & 0xFFFF; getlen -= 8; return (i >> 8) & 0xFF; }
    private int decodeChar() {
        int c = son[R];
        while (c < T) { c += getBit(); c = son[c]; }
        c -= T; update(c);
        return c;
    }
    private int decodePosition() {
        int i = getByte(), c = D_CODE[i] << 6, j = D_LEN[i];
        for (j -= 2; j > 0; j--) i = (i << 1) + getBit();
        return c | (i & 0x3F);
    }

    /** Length (32-bit LE) + bit stream, without the B2 CRC. */
    static byte[] decompress(byte[] in) throws IOException {
        if (in.length < 4) throw new IOException("lzhuf: too short");
        long size = (in[0] & 0xFFL) | ((in[1] & 0xFFL) << 8) | ((in[2] & 0xFFL) << 16) | ((in[3] & 0xFFL) << 24);
        if (size > 16L << 20) throw new IOException("lzhuf: " + size + " bytes is too large");
        Lzhuf z = new Lzhuf(); z.src = in; z.srcPos = 4;
        byte[] outb = new byte[(int) size]; int n = 0;
        for (int i = 0; i < N - F; i++) z.text[i] = ' ';
        int r = N - F;
        while (n < size) {
            if (z.srcPos > in.length + 3) throw new IOException("lzhuf: data ends early");
            int c = z.decodeChar();
            if (c < 256) { outb[n++] = (byte) c; z.text[r++] = c; r &= N - 1; continue; }
            int i = (r - z.decodePosition() - 1) & (N - 1), j = c - 255 + THRESHOLD;
            for (int k = 0; k < j && n < size; k++) { int b = z.text[(i + k) & (N - 1)]; outb[n++] = (byte) b; z.text[r++] = b; r &= N - 1; }
        }
        return outb;
    }
}
