/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development adapter, 2026-10-27. See COPYRIGHT. */
package com.macdmr.radio;

import java.io.IOException;

/** Prepared but disabled root-free open/fstat/close test. No read/write/ioctl entry point.
 * It intentionally does not use stock JNI: the stock opener configures the UART, which is beyond this first test.
 * There is no UI control to enable this. A separately reviewed build AND fresh ownership evidence are required.
 */
public final class P8OpenProbe {
    private P8OpenProbe() { }
    interface Backend { String openAndClose(); }
    static String execute(P8Ownership.Evidence evidence, long nowMs, boolean reviewed, Backend backend) throws IOException {
        String blocked = P8Ownership.openProbeBlockReason(evidence, nowMs, reviewed);
        if (blocked != null) throw new IOException(blocked);
        return backend.openAndClose();
    }
    public static String runReviewed(P8Ownership.Evidence evidence, long nowMs) throws IOException {
        if (!BuildConfig.P8_OPEN_PROBE_REVIEWED) throw new IOException("Live open probe disabled in this build");
        return execute(evidence, nowMs, true, () -> {
            System.loadLibrary("p8probejni");
            return nativeOpenAndClose();
        });
    }
    private static native String nativeOpenAndClose();
}
