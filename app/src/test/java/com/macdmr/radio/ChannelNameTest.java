/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class ChannelNameTest {
    @Test public void oneLimitOf32() {
        assertEquals(32, ChannelStore.NAME_MAX);
    }

    @Test public void namesAreTrimmedAndCut() {
        assertEquals("", ChannelStore.fitName(null));
        assertEquals("Repeater North", ChannelStore.fitName("  Repeater North  "));
        String full = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345";                    // exactly 32
        assertEquals(full, ChannelStore.fitName(full));
        assertEquals(full, ChannelStore.fitName(full + "6789"));
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ01234", ChannelStore.fitName("ABCDEFGHIJKLMNOPQRSTUVWXYZ01234 tail"));   // no trailing space left
    }

    @Test public void webEditorTakesTheLimitFromTheServer() throws Exception {
        String js = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/web/editor.js")), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse("no hard-coded 16 any more", js.contains("maxlength=\"16\""));
        assertTrue(js.contains("ED.nameMax = j.nameMax"));
    }
}
