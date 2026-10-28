/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import com.macdmr.radio.dsp.ToneTable;

/**
 * Tone codes of the W6's SA818-class module ({@code AT+DMOSETGROUP} fields 4 and 5), read from the stock app's resource
 * arrays (2026-10-27): 0 = no tone; the 51 CTCSS tones in SignalDeck's own order (62.5 … 254.1 Hz) map to the codes
 * below, the 38 standard tones being 1–38 and the 13 others 240–252; the 83 DCS codes (normal polarity) are 39–121 in
 * the same order as SignalDeck's DCS table. Inverted DCS has no code on this module.
 */
public final class Sa818Tones {
    private Sa818Tones() { }

    /** The module's code for each of SignalDeck's CTCSS sub-codes (index = SignalDeck sub-code). */
    static final int[] CTCSS_CODE = { 240, 1, 241, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 242, 26, 243, 27,
            244, 28, 245, 29, 246, 30, 247, 31, 248, 249, 32, 250, 33, 34, 35, 251, 36, 37, 38, 252 };
    /** The tones the module's list names, in the same order (Hz), to check the two lists agree. */
    static final double[] CTCSS_HZ = { 62.5, 67.0, 69.3, 71.9, 74.4, 77.0, 79.7, 82.5, 85.4, 88.5, 91.5, 94.8, 97.4, 100.0, 103.5, 107.2, 110.9, 114.8, 118.8,
            123.0, 127.3, 131.8, 136.5, 141.3, 146.2, 151.4, 156.7, 159.8, 162.2, 165.5, 167.9, 171.3, 173.8, 177.3, 179.9, 183.5, 186.2, 189.9, 192.8,
            196.6, 199.5, 203.5, 206.5, 210.7, 218.1, 225.7, 229.1, 233.6, 241.8, 250.3, 254.1 };
    static final int DCS_FIRST = 39, DCS_COUNT = 83;

    /**
     * SignalDeck's tone (type 0 none, 1 CTCSS, 2 DCS normal, 3 DCS inverted; sub-code) to the module's code, or -1 when the
     * module has no code for it (inverted DCS, an index out of range).
     */
    public static int toModule(int type, int sub) {
        if (type == 0) return 0;
        if (type == 1) {
            double hz = ToneTable.ctcssHz(sub);
            for (int i = 0; i < CTCSS_HZ.length; i++) if (Math.abs(CTCSS_HZ[i] - hz) < 0.05) return CTCSS_CODE[i];
            return -1;
        }
        if (type == 2 && sub >= 0 && sub < DCS_COUNT) return DCS_FIRST + sub;
        return -1;
    }

    /** The module's code to SignalDeck's {type, sub-code}; {0, 0} for no tone or a code the module's lists do not name. */
    public static int[] fromModule(int code) {
        if (code >= DCS_FIRST && code < DCS_FIRST + DCS_COUNT) return new int[] { 2, code - DCS_FIRST };
        for (int i = 0; i < CTCSS_CODE.length; i++) if (CTCSS_CODE[i] == code) {
            double hz = CTCSS_HZ[i];
            for (int k = 0; k < ToneTable.CTCSS.length; k++) if (Math.abs(ToneTable.CTCSS[k] - hz) < 0.05) return new int[] { 1, k };
        }
        return new int[] { 0, 0 };
    }
}
