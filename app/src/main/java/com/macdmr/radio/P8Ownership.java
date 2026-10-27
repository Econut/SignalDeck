/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development adapter, 2026-10-27. See COPYRIGHT. */
package com.macdmr.radio;

/** Root-free ownership policy, not a process killer. Missing observations always block access.
 * A package being stopped alone does not prove that another process has no UART descriptor.
 */
public final class P8Ownership {
    private P8Ownership() { }
    public enum StockState { RUNNING, STOPPED, UNKNOWN }
    public static final long MAX_AGE_MS = 5000;
    public static final class Evidence {
        public final StockState stock;
        public final boolean stockProcessAbsent, holderScanComplete;
        public final int holders;
        /** -1 means unread/unknown, 0 asserted TX, 1 stock release value. */
        public final int ptt;
        public final long observedAtMs;
        public Evidence(StockState stock, boolean stockProcessAbsent, boolean holderScanComplete,
                        int holders, int ptt, long observedAtMs) {
            this.stock = stock; this.stockProcessAbsent = stockProcessAbsent; this.holderScanComplete = holderScanComplete;
            this.holders = holders; this.ptt = ptt; this.observedAtMs = observedAtMs;
        }
    }
    public static String openProbeBlockReason(Evidence e, long nowMs, boolean reviewed) {
        if (!reviewed) return "Live UART-open test has not been reviewed/approved";
        if (e == null) return "No ownership evidence";
        if (e.observedAtMs < 0 || nowMs < e.observedAtMs || nowMs-e.observedAtMs > MAX_AGE_MS) return "Ownership evidence is stale or invalid";
        if (e.stock != StockState.STOPPED) return "Stock Intercom is running or its stopped state is unknown";
        if (!e.stockProcessAbsent) return "Stock process absence is not confirmed";
        if (!e.holderScanComplete) return "UART holder visibility is incomplete; absence cannot be inferred";
        if (e.holders != 0) return "UART holders are present or unknown";
        if (e.ptt != 1) return "PTT release state is not confirmed; do not change it automatically";
        return null;
    }
}
