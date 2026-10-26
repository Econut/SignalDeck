/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

/** The report sections added after the Atom XL and Armor 3WT reports. All inputs are made up in the shape the tools print. */
public class DeviceReportGapsTest {
    @Test public void gsiIsNamedFromTheVendorPartition() {
        assertEquals("Ulefone Armor 3WT (board s62v71c2k_gq_3wt)", DeviceReport.vendorName("TrebleDroid with GApps", "Ulefone", "Armor 3WT", "s62v71c2k_gq_3wt"));
        assertEquals("unknown (board g63v71c2k)", DeviceReport.vendorName("phh gsi", "", "", "g63v71c2k"));
        assertNull(DeviceReport.vendorName("Armor 26 Ultra", "Ulefone", "Armor 26 Ultra", "board1"));   // a stock ROM names itself
        assertNull(DeviceReport.vendorName("Atom_XL", "", "", ""));
        assertEquals("Unihertz Atom_XL", DeviceReport.vendorName("Pixel GSI", "Unihertz", "Atom_XL", "unknown"));
    }

    @Test public void usbInterfaceClasses() {
        assertEquals("audio", DeviceReport.usbClass("01"));
        assertEquals("serial (CDC)", DeviceReport.usbClass("02\n"));
        assertEquals("CDC data", DeviceReport.usbClass("0A"));
        assertEquals("class 42", DeviceReport.usbClass("42"));
        assertEquals("?", DeviceReport.usbClass(""));
    }

    @Test public void rawScancodesFromGetevent() {
        String ev = "[    1.000] /dev/input/event1: EV_KEY       00f9                 DOWN\n"
                + "[    1.000] /dev/input/event1: EV_SYN       SYN_REPORT           00000000\n"
                + "[    1.200] /dev/input/event1: EV_KEY       00f9                 UP\n"
                + "[    2.000] /dev/input/event0: EV_KEY       KEY_VOLUMEDOWN       DOWN\n";
        List<String[]> k = DeviceReport.rawKeys(ev);
        assertEquals(1, k.size());
        assertArrayEquals(new String[] { "/dev/input/event1", "00f9" }, k.get(0));
    }

    @Test public void inputDeviceAndItsLayout() {
        String dumpsys = "Event Hub State:\n  Devices:\n    -1: Virtual\n      Path: <virtual>\n      KeyLayoutFile: /system/usr/keylayout/Generic.kl\n"
                + "    2: mtk-kpd\n      Classes: KEYBOARD\n      Path: /dev/input/event1\n      Enabled: true\n      KeyLayoutFile: /system/usr/keylayout/Vendor_2454_Product_6500_Version_0010.kl\n"
                + "    3: ACCDET\n      Path: /dev/input/event10\n      KeyLayoutFile: /system/usr/keylayout/ACCDET.kl\n";
        assertArrayEquals(new String[] { "mtk-kpd", "/system/usr/keylayout/Vendor_2454_Product_6500_Version_0010.kl" }, DeviceReport.inputDevice(dumpsys, "/dev/input/event1"));
        assertArrayEquals(new String[] { "ACCDET", "/system/usr/keylayout/ACCDET.kl" }, DeviceReport.inputDevice(dumpsys, "/dev/input/event10"));
        assertArrayEquals(new String[] { "", "" }, DeviceReport.inputDevice(dumpsys, "/dev/input/event7"));
    }

    @Test public void ownHarmlessDenialsAreCountedNotListed() {
        String pkg = "com.macdmr.radio";
        String raw = "avc: denied { search } for comm=\"su\" name=\"tests\" scontext=u:r:untrusted_app:s0 tcontext=u:object_r:shell_test_data_file:s0 tclass=dir permissive=0 app=com.macdmr.radio\n"
                + "avc: denied { search } for name=\"tests\" scontext=u:r:untrusted_app:s0 tcontext=u:object_r:shell_test_data_file:s0 tclass=dir permissive=0 app=com.macdmr.radio\n"
                + "avc: denied { read } for name=\"platform\" tcontext=u:object_r:sysfs:s0 tclass=dir permissive=0 app=com.macdmr.radio\n"
                + "avc: denied { read } for name=\"x\" tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0 app=com.other.app\n";
        String out = DeviceReport.labelDenials(raw, pkg);
        assertFalse(out.contains("shell_test_data_file"));
        assertTrue(out.contains("(+2 of SignalDeck's own root calls"));
        assertTrue(out.contains("  [SignalDeck] avc: denied { read } for name=\"platform\""));
        assertTrue(out.contains("\n  avc: denied { read } for name=\"x\""));
        assertEquals("  (none)\n", DeviceReport.labelDenials("", pkg));
    }

    /** The mask as it was before the Agold frames (2026-10-25): an Armor or Xplore report must come out exactly the same. */
    private static String oldMask(String line) {
        return line.replaceAll("\\b\\d{6,}\\b", "######").replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "***@***")
                .replaceAll("(?<![\\w.])[A-Z]{1,2}[0-9][A-Z]{1,3}(-[0-9]{1,2})?(?![\\w.])", "CALL");
    }

    @Test public void reportsWithoutAgoldFramesAreUnchanged() {
        String[] lines = {
            "10-25 09:46:02.946  1234  1240 I SerialPortManager: send 68 34 01 01 95 b9 00 01 01 10",
            "10-25 09:46:03.100  1234  1240 I DmrManager: rx 68 22 01 00 12 34 00 9c 7e 96 00 00 aa bb 10",   // the Ulefone frame family, a 7E byte inside
            "10-25 09:46:04.000  1234  1240 D RadioService: caller 3141592 on TG 91, from N0CALL-7 mail a.b@example.org",
            "module DMR003.UV4T.V022 answered in 120 ms",
            "hex run 7E9669 without a frame header",
        };
        for (String l : lines) assertEquals(l, oldMask(l), DeviceReport.mask(l));
    }

    @Test public void listeningPortsAndConnections() {
        String tcp = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n"
                + "   0: 0100007F:26FC 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 4711 1 0000000000000000 100 0 0 10 0\n"
                + "   1: 0100007F:2701 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 4712 1 0000000000000000 100 0 0 10 0\n"
                + "   2: 0100007F:9C40 0100007F:26FC 01 00000000:00000000 00:00000000 00000000 10277        0 5001 1 0000000000000000 20 4 30 10 -1\n";
        List<String[]> t = DeviceReport.tcpTable(tcp);
        assertEquals(3, t.size());
        assertArrayEquals(new String[] { "127.0.0.1", "26FC", "0000", "0A", "4711" }, t.get(0));
        assertEquals(9980, Integer.parseInt(t.get(0)[1], 16));
        String ls = "/proc/812/fd:\nlrwx------ 1 root root 64 2026-10-25 09:46 5 -> socket:[4711]\nlrwx------ 1 root root 64 2026-10-25 09:46 6 -> socket:[4712]\n"
                + "/proc/20402/fd:\nlrwx------ 1 u0_a1 u0_a1 64 2026-10-25 09:46 80 -> socket:[5001]\n";
        java.util.Map<String, String> own = DeviceReport.socketOwners(ls);
        assertEquals("812", own.get("4711")); assertEquals("812", own.get("4712")); assertEquals("20402", own.get("5001"));
        assertEquals("40000 -> 9980 (connected);", DeviceReport.tcpOf(tcp, "lrwx------ 1 u0_a1 u0_a1 64 2026-10-25 09:46 80 -> socket:[5001]\n"));
        assertEquals("(none)", DeviceReport.tcpOf(tcp, "lrwx------ 1 u0_a1 u0_a1 64 2026-10-25 09:46 3 -> /dev/null\n"));
        assertEquals("[::]", DeviceReport.addr("00000000000000000000000000000000"));
        assertEquals("127.0.0.1", DeviceReport.addr("0000000000000000FFFF00000100007F"));
    }
}
