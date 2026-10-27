/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class DeviceReportTest {
    private static File lib(String strings) throws Exception {
        File f = File.createTempFile("lib", ".so"); f.deleteOnExit();
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(("\u007fELF\u0000" + strings).getBytes(StandardCharsets.ISO_8859_1)); }
        return f;
    }

    /** The Xplore 1's libwonder_serialport.so exports no Java_ symbols: it registers its methods from JNI_OnLoad. */
    @Test public void dynamicallyRegisteredSerialLibraryIsDescribed() throws Exception {
        File f = lib("JNI_OnLoad\u0000cfsetispeed\u0000com/wonder/serial/SerialPort\u0000openSerial\u0000setupSerial\u0000readData\u0000"
                + "(Ljava/lang/String;I)I\u0000(IIICI)I\u0000(I[BI)I\u0000/system/lib64/libc.so\u0000");
        assertEquals("", DeviceReport.jniSymbols(f));
        String r = DeviceReport.registeredNatives(f);
        assertTrue(r, r.contains("com.wonder.serial.SerialPort"));
        assertTrue(r, r.contains("(Ljava/lang/String;I)I") && r.contains("(IIICI)I") && r.contains("(I[BI)I"));
        assertTrue(r, r.contains("openSerial") && r.contains("setupSerial") && r.contains("readData"));
        assertFalse("system paths are not classes", r.contains("system.lib64"));
    }

    @Test public void appLogKeepsRadioStepsAndDropsPersonalLines() {
        String out = DeviceReport.redactAppLog(java.util.Arrays.asList(
                "12:00:00.000 sepolicy: ok",
                "12:00:01.000 module power -> on (pwd :1)",
                "12:00:02.000 tuned 146.520 MHz",
                "12:00:03.000 APRS N0CALL-9>APDR16: position 38.12345,-122.54321",
                "12:00:04.000 caller 3112345 talking",
                "12:00:05.000 DMR ID from the stock app: 3112345",
                "12:00:06.000 web PIN changed",
                "12:00:07.000 bring-up failed: W1AW said hello at 38.12345 -122.54321",
                "12:00:08.000 module DMR003.UV4T.V022",
                "12:00:09.000 TX cmd=0x2C rw=1 sr=1 len=9 [01 41 00 42 00]  <68 2C 01 01 41 00 42 00 10>",
                "12:00:10.000 TX cmd=0x23 rw=1 sr=1 len=2 [A0 8D]  <68 23 A0 8D 10>"));
        assertTrue(out, out.contains("module DMR003.UV4T.V022"));
        assertTrue(out, out.contains("TX cmd=0x2C rw=1 sr=1 len=9 [\u2026]  <\u2026>") && out.contains("[A0 8D]  <68 23 A0 8D 10>"));
        assertTrue(out, out.contains("sepolicy: ok") && out.contains("module power -> on (pwd :1)") && out.contains("146.520 MHz"));
        assertFalse(out, out.contains("N0CALL") || out.contains("APRS") || out.contains("talking") || out.contains("PIN"));
        assertTrue(out, out.contains("DMR ID from the stock app: ######"));
        assertTrue(out, out.contains("bring-up failed: CALL said hello at #.#### #.####"));
        assertFalse(out, out.contains("3112345") || out.contains("W1AW") || out.contains("38.12345"));
    }

    @Test public void apkCluesFindPathsParametersSpeedsAndPttTexts() throws Exception {
        File apk = File.createTempFile("stock", ".apk"); apk.deleteOnExit();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(apk))) {
            z.putNextEntry(new java.util.zip.ZipEntry("classes.dex"));
            z.write(("dex\n035\u0000\u0001/sys/devices/platform/intercom/intercom_ptt_control\u0000\u0002/dev/ttyS1\u0000\u0003pdt_play=off\u0000"
                    + "\u0004B230400\u0000\u0005 setPttLaunch  , set radio to transmit\u0000\u0006Lcom/wonder/dmr/DmrManager;\u0000\u0007unrelated text\u0000")
                    .getBytes(StandardCharsets.ISO_8859_1));
            z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("res/raw/song.bin")); z.write("/sys/not/code".getBytes()); z.closeEntry();
        }
        String r = DeviceReport.apkClues(apk);
        assertTrue(r, r.contains("/sys/devices/platform/intercom/intercom_ptt_control") && r.contains("/dev/ttyS1"));
        assertTrue(r, r.contains("pdt_play=off") && r.contains("B230400"));
        assertTrue(r, r.contains("setPttLaunch"));
        assertFalse(r, r.contains("/sys/not/code") || r.contains("unrelated") || r.contains("DmrManager"));
    }

    @Test public void apkCluesOnA32BitAppWithAtCommands() throws Exception {
        // the W6's stock app: one dex with SA818-class commands, its serial library only for armeabi-v7a
        File apk = File.createTempFile("w6stock", ".apk"); apk.deleteOnExit();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(apk))) {
            z.putNextEntry(new java.util.zip.ZipEntry("classes.dex"));
            z.write(("dex\n035\u0000\u0001AT+DMOCONNECT\u0000\u0001AT+DMOSETGROUP=\u0000\u0001AT+DMOSETVOLUME=\u0000\u0001AT+DMOVERQ\u0000"
                    + "\u0001, isPttButton ==\u0000\u0001FPTt\\@n\u0000\u0001in onInterceptTouchEvent\u0000").getBytes(StandardCharsets.ISO_8859_1));
            z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("lib/armeabi-v7a/libserial_port.so"));
            z.write("\u007fELF....Opening serial port /dev/ttyS2 with flags\u0000".getBytes(StandardCharsets.ISO_8859_1));
            z.closeEntry();
        }
        String r = DeviceReport.apkClues(apk);
        assertTrue(r, r.contains("  paths: /dev/ttyS2\n"));                                     // from the 32-bit library
        assertTrue(r, r.contains("  native libraries: lib/armeabi-v7a/libserial_port.so\n"));
        assertTrue(r, r.contains("  module commands: AT+DMOCONNECT  AT+DMOSETGROUP=  AT+DMOSETVOLUME=  AT+DMOVERQ\n"));
        assertTrue(r, r.contains("  protocol hint from its code: SA818-class AT commands (4 AT+DMO… names)\n"));
        assertTrue(r, r.contains("isPttButton"));
        assertFalse(r, r.contains("FPTt") || r.contains("onInterceptTouchEvent"));            // noise the 2026-10-27 report listed
        assertNull(DeviceReport.commandFamily(java.util.List.of("AT+CGMI")));
    }

    @Test public void deviceTreeValuesAsTextOrCells() {
        assertEquals("\"coosea,dmr009\"", DeviceReport.dtValue("63 6f 6f 73 65 61 2c 64 6d 72 30 30 39 00"));
        assertEquals("\"default|sleep\"", DeviceReport.dtValue("64 65 66 61 75 6c 74 00 73 6c 65 65 70 00"));
        assertEquals("<0x00000012 0x0000009c 0x00000000>", DeviceReport.dtValue("00 00 00 12 00 00 00 9c 00 00 00 00"));
        // a pin-name list longer than the read limit (Xplore 1 report, 2026-10-24): still text, marked as cut
        StringBuilder big = new StringBuilder(); String names = "default\0intercom_dmr_oe_en_low\0";
        while (big.length() < DeviceReport.DT_MAX * 3) for (char ch : names.toCharArray()) big.append(String.format("%02x ", (int) ch));
        String cutHex = big.substring(0, DeviceReport.DT_MAX * 3);
        String v = DeviceReport.dtValue(cutHex);
        assertTrue(v, v.startsWith("\"default|intercom_dmr_oe_en_low|default") && v.endsWith("\u2026\""));
    }

    @Test public void exportedSymbolsStillWinAndPlainLibrariesSayNothing() throws Exception {
        File f = lib("Java_com_pri_prizeinterphone_serial_port_SerialPort_open\u0000");
        assertTrue(DeviceReport.jniSymbols(f).contains("com.pri.prizeinterphone.serial.port.SerialPort.open()"));
        assertEquals("", DeviceReport.registeredNatives(lib("nothing here\u0000")));
    }
}
