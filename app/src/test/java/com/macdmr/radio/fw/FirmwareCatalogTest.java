/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio.fw;

import static org.junit.Assert.*;

import com.macdmr.radio.ProfileRegistry;

import org.junit.Test;

public class FirmwareCatalogTest {
    private static byte[] json() throws Exception {
        return FirmwareCatalogTest.class.getResourceAsStream("/fw/firmware.json").readAllBytes();
    }

    private static String sig() throws Exception {
        return new String(FirmwareCatalogTest.class.getResourceAsStream("/fw/firmware.json.sig").readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim();
    }

    @Test public void thePublishedCatalogueVerifies() throws Exception {
        assertTrue(ProfileRegistry.verify(json(), sig(), ProfileRegistry.PUBLIC_KEY));
        FirmwareCatalog.Doc doc = FirmwareCatalog.accept(json(), sig(), 0);
        assertNotNull(doc);
        assertTrue(doc.serial >= 4);
        assertEquals(1, doc.images.size());
        assertEquals(ImageTable.FACTORY.tag, doc.images.get(0).tag);
        assertEquals(ImageTable.FACTORY.sha256, doc.images.get(0).sha256);
        assertTrue(doc.images.get(0).factory);
        assertTrue(doc.variants.stream().anyMatch(v -> v.supportedBase && "com.pri.prizeinterphone".equals(v.stockPackage)));
        assertTrue("serial 4 names the phone in the Armor block", doc.variants.stream().anyMatch(v -> "ulefone-armor-26-ultra".equals(v.device) && "Armor 26 Ultra".equals(v.buildModel)));
    }

    @Test public void aBadSignatureIsIgnored() throws Exception {
        assertNull(FirmwareCatalog.accept(json(), sig() + "x", 0));
    }

    @Test public void anOlderSerialIsIgnored() throws Exception {
        FirmwareCatalog.Doc doc = FirmwareCatalog.accept(json(), sig(), 0);
        assertNull(FirmwareCatalog.accept(json(), sig(), doc.serial + 1));
        assertNotNull(FirmwareCatalog.accept(json(), sig(), doc.serial - 1));
    }
}
