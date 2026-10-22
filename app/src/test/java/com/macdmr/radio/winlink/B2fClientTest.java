/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class B2fClientTest {
    @Test public void secureLoginMatchesReference() {
        assertEquals("72768415", B2fClient.secureLoginResponse("23753528", "FOOBAR"));   // wl2k-go's test vectors
        assertEquals("95074758", B2fClient.secureLoginResponse("23753528", "FooBar"));
    }

    @Test public void messageRoundTrip() throws IOException {
        B2Message m = B2Message.create("n0call", "Test æøå", "Line one\nLine two\n");
        m.to.add(B2Message.address("someone@example.com")); m.to.add(B2Message.address("n0abc@winlink.org"));
        m.files.add(new B2Message.Attachment("pic.jpg", new byte[] { 1, 2, 3, 13, 10, 0, (byte) 255 }));
        byte[] b = m.toBytes();
        String head = new String(b, StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("Mid: " + m.mid + "\r\nBody: 20\r\n"));
        assertTrue(head.contains("\r\nTo: SMTP:someone@example.com\r\nTo: N0ABC\r\n"));
        assertTrue(head.contains("\r\nFile: 7 pic.jpg\r\n"));
        B2Message p = B2Message.parse(b);
        assertEquals(m.mid, p.mid); assertEquals(m.subject, p.subject); assertEquals("Line one\r\nLine two\r\n", p.bodyText());
        assertEquals(2, p.to.size()); assertEquals("N0CALL", p.from);
        assertEquals(1, p.files.size()); assertArrayEquals(m.files.get(0).data, p.files.get(0).data);
        assertEquals(12, m.mid.length());
        assertTrue(B2Message.parseDate(m.date) > 0);
    }

    /** A fake CMS: banner + secure login, accepts our message, sends one of its own, then both sides finish. */
    @Test public void fullExchangeWithFakeGateway() throws Exception {
        PipedOutputStream toClient = new PipedOutputStream(); PipedInputStream clientIn = new PipedInputStream(toClient, 1 << 20);
        PipedOutputStream toGw = new PipedOutputStream(); PipedInputStream gwIn = new PipedInputStream(toGw, 1 << 20);
        AtomicReference<Throwable> gwError = new AtomicReference<>(); AtomicReference<B2Message> gwGot = new AtomicReference<>();
        B2Message theirs = B2Message.create("WL2K", "Hello from the gateway", "Welcome.\n"); theirs.to.add("N0CALL");
        Thread gw = new Thread(() -> {
            try { fakeGateway(gwIn, toClient, theirs, gwGot); } catch (Throwable t) { gwError.set(t); }
        });
        gw.start();

        B2Message mine = B2Message.create("N0CALL", "Test from SignalDeck", "Hello Winlink.\n");
        mine.to.add(B2Message.address("someone@example.com"));
        List<B2Message> outbox = new ArrayList<>(); outbox.add(mine);
        List<B2Message> inbox = new ArrayList<>(); List<String> log = new ArrayList<>();
        B2fClient.Mailbox box = new B2fClient.Mailbox() {
            @Override public List<B2Message> outbox() { return new ArrayList<>(outbox); }
            @Override public boolean have(String mid) { for (B2Message x : inbox) if (x.mid.equals(mid)) return true; return false; }
            @Override public void received(B2Message m) { inbox.add(m); }
            @Override public void sent(String mid) { outbox.removeIf(x -> x.mid.equals(mid)); }
            @Override public void log(String line) { log.add(line); }
        };
        B2fClient.Wire wire = new B2fClient.Wire() {
            @Override public int read(long t) throws IOException { int c = clientIn.read(); if (c < 0) throw new IOException("closed"); return c; }
            @Override public void write(byte[] b) throws IOException { toGw.write(b); toGw.flush(); }
        };
        B2fClient c = new B2fClient(wire, box, "N0CALL", "WL2K", "AA00aa", "1.0.0-beta.13", "FOOBAR", 5000);
        c.run();
        gw.join(5000);
        if (gwError.get() != null) throw new AssertionError("gateway: " + gwError.get(), gwError.get());
        assertEquals(1, c.sent()); assertEquals(1, c.received());
        assertTrue(outbox.isEmpty());
        assertEquals("Hello from the gateway", inbox.get(0).subject); assertEquals("Welcome.\r\n", inbox.get(0).bodyText());
        assertEquals(mine.mid, gwGot.get().mid); assertEquals("Hello Winlink.\r\n", gwGot.get().bodyText());
        for (String l : log) assertFalse("password in the log", l.contains("FOOBAR"));
        assertTrue(log.contains("> ;PR: ********"));
    }

    // ---- the fake gateway (independent of B2fClient's code) ---------------------------------------------

    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (;;) { int c = in.read(); if (c < 0) throw new IOException("eof"); if (c == '\r') return sb.toString(); if (c != '\n') sb.append((char) c); }
    }
    static void send(OutputStream o, String s) throws IOException { o.write(s.getBytes(StandardCharsets.ISO_8859_1)); o.flush(); }

    static void fakeGateway(InputStream in, OutputStream out, B2Message theirs, AtomicReference<B2Message> got) throws Exception {
        send(out, "Welcome to the test CMS\r[WL2K-5.0-B2FWIHJM$]\r;PQ: 23753528\rCMS via TEST >\r");
        String fw = line(in), sid = line(in), pr = line(in), de = line(in);
        assertEquals(";FW: N0CALL", fw);
        assertTrue(sid, sid.matches("\\[SignalDeck-1\\.0\\.0\\.beta\\.13-B2FHM\\$\\]"));
        assertEquals(";PR: 72768415", pr);
        assertTrue(de, de.startsWith("; WL2K DE N0CALL"));
        // client's proposals
        String prop = line(in), chk = line(in);
        int sum = 0; for (char c : prop.toCharArray()) sum += c; sum += '\r';
        assertEquals(String.format("F> %02X", (-sum) & 0xFF), chk);
        String[] p = prop.split(" ");
        assertEquals("FC", p[0]); assertEquals("EM", p[1]);
        int size = Integer.parseInt(p[3]), csize = Integer.parseInt(p[4]);
        send(out, "FS Y\r");
        assertEquals(1, in.read()); int hl = in.read(); StringBuilder title = new StringBuilder(); int c; while ((c = in.read()) != 0) title.append((char) c);
        StringBuilder off = new StringBuilder(); while ((c = in.read()) != 0) off.append((char) c);
        assertEquals(hl, title.length() + off.length() + 2); assertEquals("0", off.toString()); assertEquals("Test from SignalDeck", title.toString());
        ByteArrayOutputStream data = new ByteArrayOutputStream(); int s2 = 0;
        for (;;) { int k = in.read(); if (k == 2) { int n = in.read(); for (int i = 0; i < n; i++) { int b = in.read(); data.write(b); s2 += b; } } else { assertEquals(4, k); s2 += in.read(); break; } }
        assertEquals(0, s2 & 0xFF); assertEquals(csize, data.size());
        byte[] plain = Lzhuf.decompressB2(data.toByteArray()); assertEquals(size, plain.length);
        got.set(B2Message.parse(plain));
        // our proposal
        byte[] tp = theirs.toBytes(), tc = Lzhuf.compressB2(tp);
        String fc = "FC EM " + theirs.mid + " " + tp.length + " " + tc.length + " 0"; int s3 = 0; for (char ch : fc.toCharArray()) s3 += ch; s3 += '\r';
        send(out, ";PM: N0CALL " + theirs.mid + " " + tp.length + " WL2K Hello\r" + fc + "\r" + String.format("F> %02X\r", (-s3) & 0xFF));
        assertEquals("FS +", line(in));
        ByteArrayOutputStream blk = new ByteArrayOutputStream(); byte[] t = theirs.subject.getBytes(StandardCharsets.US_ASCII);
        blk.write(1); blk.write(t.length + 3); blk.write(t); blk.write(0); blk.write('0'); blk.write(0);
        int s4 = 0; for (int i = 0; i < tc.length; i += 250) { int n = Math.min(250, tc.length - i); blk.write(2); blk.write(n); blk.write(tc, i, n); for (int k = 0; k < n; k++) s4 += tc[i + k] & 0xFF; }
        blk.write(4); blk.write((-s4) & 0xFF); out.write(blk.toByteArray()); out.flush();
        assertEquals("FF", line(in));             // the client has nothing more
        send(out, "FF\r");                        // neither do we
        assertEquals("FQ", line(in));
        out.close();
    }
}
