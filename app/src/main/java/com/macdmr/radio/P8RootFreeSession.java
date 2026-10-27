/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 development transport, 2026-10-27. See COPYRIGHT. */
package com.macdmr.radio;

import java.io.IOException;

/** Bounded development diagnostics under the owner's broader root-free authorisation.
 * Known stock process release is distinct from proof of exclusive UART ownership.
 * No complete-holder evidence is fabricated and the original strict policy stays unchanged.
 */
public final class P8RootFreeSession {
    private P8RootFreeSession() { }
    public static final int OPEN_CLOSE=0, VERSION_QUERY=1, PCM_CAPTURE=2;
    public static String blockReason(boolean reviewed, boolean stockStopped, boolean hostAbsent,
                                     long ageMs, int ptt, int mode) {
        if (!reviewed) return "Root-free development diagnostics disabled";
        if (!stockStopped || !hostAbsent) return "Stock process release not confirmed";
        if (ageMs<0 || ageMs>5000) return "Host process observation stale";
        if (ptt!=1) return "PTT not released";
        if (mode!=OPEN_CLOSE && mode!=VERSION_QUERY && mode!=PCM_CAPTURE) return "Operation not allowlisted";
        return null;
    }
    static String run(boolean stopped, boolean absent, long ageMs, int ptt, int mode) throws IOException {
        if(P8Receiver.active())throw new IOException("Close receive session before diagnostics");
        String reason=blockReason(BuildConfig.P8_OFFLINE && BuildConfig.P8_OPEN_PROBE_REVIEWED,
                stopped,absent,ageMs,ptt,mode);
        if(reason!=null)throw new IOException(reason);
        System.loadLibrary("p8probejni");
        return nativeRun(mode);
    }
    private static native String nativeRun(int mode);
}
