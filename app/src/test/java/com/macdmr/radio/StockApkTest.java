/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class StockApkTest {
    @Test public void pmPathOutputGivesTheApks() {
        assertEquals(Arrays.asList("/system/priv-app/Stock/Stock.apk", "/data/app/~~x==/com.example-y==/split_config.arm64_v8a.apk"),
                StockApk.parsePmPath("package:/system/priv-app/Stock/Stock.apk\r\npackage:/data/app/~~x==/com.example-y==/split_config.arm64_v8a.apk\n"
                        + "package:/system/priv-app/Stock/Stock.apk\n"));
    }

    @Test public void anythingElseIsIgnored() {
        assertTrue(StockApk.parsePmPath(null).isEmpty());
        assertTrue(StockApk.parsePmPath("").isEmpty());
        assertTrue(StockApk.parsePmPath("Error: package not found\npackage:relative.apk\npackage:/system/framework/x.jar\n").isEmpty());
    }
}
