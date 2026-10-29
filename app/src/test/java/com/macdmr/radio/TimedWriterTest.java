/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

/** A write the device never takes (the Atom XL's bridge, 2026-10-29) fails after its deadline instead of freezing the caller. */
public class TimedWriterTest {
    /** Blocks every write until released, like /dev/ttyACM0 when the bridge stopped taking data. */
    static final class StuckStream extends OutputStream {
        final CountDownLatch release = new CountDownLatch(1);
        final ByteArrayOutputStream got = new ByteArrayOutputStream();
        @Override public void write(int b) throws IOException {
            try { release.await(); } catch (InterruptedException e) { throw new IOException("interrupted"); }
            got.write(b);
        }
    }

    @Test public void aWriteThatNeverFinishesFailsAfterTheDeadline() throws Exception {
        StuckStream s = new StuckStream();
        TimedWriter w = new TimedWriter(s, 200, "test-writer");
        long t0 = System.currentTimeMillis();
        try { w.write(new byte[] { 1, 2, 3 }); fail("should time out"); }
        catch (IOException e) { assertTrue(e.getMessage(), e.getMessage().contains("did not finish")); }
        long took = System.currentTimeMillis() - t0;
        assertTrue("returned after the deadline, not forever: " + took + " ms", took >= 180 && took < 2000);
        assertTrue(w.stalled());

        t0 = System.currentTimeMillis();                                     // the stuck write is still blocked: the next one fails at once
        try { w.write(new byte[] { 4 }); fail("should refuse while the last write is stuck"); }
        catch (IOException e) { assertTrue(e.getMessage(), e.getMessage().contains("has not finished")); }
        assertTrue(System.currentTimeMillis() - t0 < 100);

        s.release.countDown();                                               // the device takes data again
        Thread.sleep(100);
        w.write(new byte[] { 5 });
        assertFalse("a finished write clears the stall", w.stalled());
        w.shutdown();
    }

    @Test public void ordinaryWritesPassStraightThrough() throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        TimedWriter w = new TimedWriter(o, 200, "test-writer");
        w.write(new byte[] { 0x7E, (byte) 0x96, 0x69 });
        w.write(new byte[] { (byte) 0x81 });
        assertArrayEquals(new byte[] { 0x7E, (byte) 0x96, 0x69, (byte) 0x81 }, o.toByteArray());
        assertFalse(w.stalled());
        w.shutdown();
    }

    @Test public void aFailingWriteIsReportedAsItsOwnError() {
        TimedWriter w = new TimedWriter(new OutputStream() { @Override public void write(int b) throws IOException { throw new IOException("I/O error"); } }, 200, "test-writer");
        try { w.write(new byte[] { 1 }); fail(); } catch (IOException e) { assertEquals("I/O error", e.getMessage()); }
        assertFalse("an error is not a stall", w.stalled());
        w.shutdown();
    }
}
