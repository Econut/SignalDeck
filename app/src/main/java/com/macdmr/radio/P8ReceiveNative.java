/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive-only transport. See COPYRIGHT. */
package com.macdmr.radio;
import java.io.IOException;
final class P8ReceiveNative {
    private P8ReceiveNative() { }
    static void load() throws IOException {
        if(!BuildConfig.P8_OFFLINE || !BuildConfig.P8_OPEN_PROBE_REVIEWED)throw new IOException("RX development build required");
        System.loadLibrary("p8rxjni");
    }
    static native void open() throws IOException;
    static native byte[] tune(byte[] packet) throws IOException;
    static native byte[] readPcm() throws IOException;
    static native byte[] readControl() throws IOException;
    static native void close() throws IOException;
}
