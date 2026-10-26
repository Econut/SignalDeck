/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class FirmwareFinderTest {
    @Test public void versionStringsInsideAnImage() {
        byte[] img = new byte[4096];
        byte[] a = "xxDMR006.TEST.V082\0".getBytes(), b = "ver0.5.4_470M_TONE".getBytes();
        System.arraycopy(a, 0, img, 100, a.length); System.arraycopy(b, 0, img, 900, b.length); System.arraycopy(a, 0, img, 2000, a.length);
        assertEquals("DMR006.TEST.V082, ver0.5.4_470M_TONE", FirmwareFinder.versionsIn(img));
        assertEquals("", FirmwareFinder.versionsIn(new byte[64]));
    }

    @Test public void onlyRadioImagesInSharedFolders() {
        assertTrue(FirmwareFinder.radioName("/vendor/firmware/dmr_module.bin"));
        assertTrue(FirmwareFinder.radioName("/vendor/firmware/MCU_20181221.bin"));
        assertFalse(FirmwareFinder.radioName("/vendor/firmware/WIFI_RAM_CODE_soc1_0_1_1.bin"));
        assertFalse(FirmwareFinder.radioName("/vendor/firmware/camera_ov13b10.bin"));
    }

    @Test public void imagesInsideAnApk() {
        assertTrue(FirmwareFinder.apkImage("assets/dmr/DMR003.bin"));
        assertTrue(FirmwareFinder.apkImage("res/raw/mcu.hex"));
        assertFalse(FirmwareFinder.apkImage("assets/web/index.html"));
        assertFalse(FirmwareFinder.apkImage("lib/arm64-v8a/libx.so"));
    }

    @Test public void copiedNamesAreSafeAndUnique() {
        java.util.Set<String> used = new java.util.HashSet<>();
        assertEquals("mcu.bin", FirmwareFinder.uniqueName(used, FirmwareFinder.safe("mcu.bin")));
        assertEquals("mcu_2.bin", FirmwareFinder.uniqueName(used, "mcu.bin"));
        assertEquals("a_b_c.bin", FirmwareFinder.safe("a b/c.bin"));
        assertEquals("00ff10", FirmwareFinder.hex(new byte[] { 0, (byte) 0xFF, 16 }));
    }
}
