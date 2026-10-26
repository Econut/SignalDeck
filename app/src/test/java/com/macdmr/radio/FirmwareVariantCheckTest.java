/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class FirmwareVariantCheckTest {
    private static String x32(String two) { StringBuilder b = new StringBuilder(); for (int i = 0; i < 32; i++) b.append(two); return b.toString(); }

    /** A catalogue like serial 5: one image row, one device block with a variant without a hash and one with. */
    private static final String CATALOGUE = "{\"format\":1,\"serial\":7,\"images\":["
            + "{\"tag\":\"DMR009.TEST.V001\",\"sha256\":\"" + x32("ab") + "\",\"status\":\"stock\"}],"
            + "\"variants\":[{\"device\":\"test-phone\",\"variants\":["
            + "{\"moduleVersion\":\"DMR009.TEST.V002\",\"assetSha256\":null},"
            + "{\"moduleVersion\":\"DMR009.TEST.V003\",\"assetSha256\":\"" + x32("CD") + "\"}]}]}";

    private static FirmwareVariantCheck.Known known() { return FirmwareVariantCheck.known(CATALOGUE.getBytes(StandardCharsets.UTF_8)); }
    private static FirmwareVariantCheck.Facts facts(String tag, String sha) { return new FirmwareVariantCheck.Facts("Test Phone 1", tag, "com.example.radio", "2.0", sha); }

    @Test public void catalogueTagsAndHashesAreRead() {
        FirmwareVariantCheck.Known k = known();
        assertEquals(7, k.serial);
        assertTrue(k.tags.containsAll(java.util.Arrays.asList("DMR009.TEST.V001", "DMR009.TEST.V002", "DMR009.TEST.V003", "DMR003.UV4T.V022")));
        assertTrue(k.shas.contains(x32("ab"))); assertTrue("hashes are compared in lower case", k.shas.contains(x32("cd")));
        assertTrue("built-in factory image", k.shas.contains(com.macdmr.radio.fw.ImageTable.FACTORY.sha256));
        assertFalse(k.shas.contains("null"));
    }

    @Test public void listedVariantsAreNotOffered() {
        FirmwareVariantCheck.Known k = known();
        assertNull(FirmwareVariantCheck.missing(facts("DMR009.TEST.V001", x32("ab")), k));
        assertNull("no hash read: the version decides", FirmwareVariantCheck.missing(facts("DMR009.TEST.V002", ""), k));
        assertNull(FirmwareVariantCheck.missing(facts("DMR009.TEST.V003", x32("CD")), k));
        assertNull("the Armor's factory image is built in", FirmwareVariantCheck.missing(facts("DMR003.UV4T.V022", com.macdmr.radio.fw.ImageTable.FACTORY.sha256), FirmwareVariantCheck.known(null)));
        assertNull("SignalDeck's own bench tags are never a stock variant", FirmwareVariantCheck.missing(facts("DMR003.FW27.V022", ""), k));
        assertNull(FirmwareVariantCheck.missing(facts("DMR003.R4a0.V022", ""), k));
        assertNull("no version yet", FirmwareVariantCheck.missing(facts("", x32("01")), k));
    }

    @Test public void newVariantsSayWhatIsMissing() {
        FirmwareVariantCheck.Known k = known();
        assertEquals("the module version DMR009.TEST.V009 is not in the firmware catalogue", FirmwareVariantCheck.missing(facts("DMR009.TEST.V009", ""), k));
        assertEquals("the module version DMR009.TEST.V009 and the stock app's firmware file are not in the firmware catalogue", FirmwareVariantCheck.missing(facts("DMR009.TEST.V009", x32("01")), k));
        assertEquals("the stock app's firmware file (sha256 " + x32("01") + ") is not in the firmware catalogue", FirmwareVariantCheck.missing(facts("DMR009.TEST.V001", x32("01")), k));
    }

    @Test public void theIssueFormIsFilledIn() {
        String u = FirmwareVariantCheck.issueUrl(facts("DMR009.TEST.V009", x32("01")), "the module version DMR009.TEST.V009 is not in the firmware catalogue", "1.0.0-beta.30");
        assertTrue(u, u.startsWith("https://github.com/IIMacGyverII/AndroidRadio_Firmware_Project/issues/new?template=firmware-variant.yml&title="));
        assertTrue(u, u.contains("&title=%5Bvariant%5D%20DMR009.TEST.V009%20on%20Test%20Phone%201"));
        assertTrue(u, u.contains("&model=Test%20Phone%201&version=DMR009.TEST.V009&stockapp=com.example.radio%202.0&hash=" + x32("01")));
        assertTrue(u, u.contains("&message=SignalDeck%201.0.0-beta.30%3A%20the%20module%20version"));
        assertFalse("nothing but the listed fields", u.contains("+"));
    }

    @Test public void oneKeyPerVariant() {
        assertEquals(facts("DMR009.TEST.V009", x32("01")).key(), new FirmwareVariantCheck.Facts("Other", " DMR009.TEST.V009 ", "x", "y", x32("01").toUpperCase()).key());
        assertNotEquals(facts("DMR009.TEST.V009", x32("01")).key(), facts("DMR009.TEST.V009", x32("02")).key());
    }
}
