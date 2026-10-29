/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Writes through one thread of its own with a deadline, so a write the device never takes cannot hold the caller. On the
 * Atom XL the radio's USB bridge once stopped taking serial data right after a bridge restart that met a charger change
 * (2026-10-29 10:11): write() to /dev/ttyACM0 blocked for good, the radio engine froze with it, and nothing noticed. Here
 * the caller gets an IOException after the deadline and {@link #stalled()} stays true until a write finishes again; while
 * the stuck write is still blocked, later writes fail at once instead of queueing behind it.
 */
final class TimedWriter {
    private final OutputStream out;
    private final long deadlineMs;
    private final ExecutorService thread;
    private Future<?> pending;
    private volatile boolean stalled;

    TimedWriter(OutputStream out, long deadlineMs, String name) {
        this.out = out; this.deadlineMs = deadlineMs;
        this.thread = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; });
    }

    /** True from a write that missed its deadline until a later write finishes. */
    boolean stalled() { return stalled; }

    synchronized void write(byte[] b) throws IOException {
        if (pending != null && !pending.isDone()) throw new IOException("the previous write has not finished after " + deadlineMs + " ms");
        pending = thread.submit(() -> { out.write(b); out.flush(); return null; });
        try {
            pending.get(deadlineMs, TimeUnit.MILLISECONDS);
            stalled = false;
        } catch (TimeoutException e) {
            stalled = true;
            throw new IOException("write did not finish in " + deadlineMs + " ms");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException ? (IOException) c : new IOException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    /** Lets the writer thread go (a write still blocked ends when the stream is closed or the device goes away). */
    void shutdown() { thread.shutdownNow(); }
}
