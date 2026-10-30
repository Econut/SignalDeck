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
    static native void recoverReleased() throws IOException;
    static native void power(boolean on) throws IOException;
    static native void open() throws IOException;
    static native void prepareTransmit() throws IOException;
    static native void acknowledgeStatus() throws IOException;
    static void key(boolean down) throws IOException {keyTimed(down,10000);}
    static native void keyTimed(boolean down,int timeoutMs) throws IOException;
    static native long writeMicrophone(byte[] pcm,int length) throws IOException;
    static native byte[] tune(byte[] packet) throws IOException;
    static native byte[] volume(int level) throws IOException;
    static native byte[] readPcm() throws IOException;
    static native byte[] readControl() throws IOException;
    static native void requestRssi() throws IOException;
    static native void close() throws IOException;
}
