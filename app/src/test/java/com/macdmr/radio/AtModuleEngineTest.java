/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import com.macdmr.radio.dsp.ToneTable;

import org.junit.Test;

/** The W6's AT engine against what the stock app was sniffed doing (2026-10-27) and its own tables. */
public class AtModuleEngineTest {
    @Test public void tonesMatchTheModulesLists() {
        assertEquals(ToneTable.CTCSS.length, Sa818Tones.CTCSS_HZ.length);                  // the same 51 tones, in the same order
        for (int i = 0; i < Sa818Tones.CTCSS_HZ.length; i++) assertEquals("tone " + i, ToneTable.CTCSS[i], Sa818Tones.CTCSS_HZ[i], 0.05);
        assertEquals(240, Sa818Tones.toModule(1, 0));                                      // 62.5 Hz, every factory channel's tone
        assertEquals(1, Sa818Tones.toModule(1, 1));                                        // 67.0 Hz, the first standard tone
        assertEquals(252, Sa818Tones.toModule(1, 50));                                     // 254.1 Hz
        assertEquals(0, Sa818Tones.toModule(0, 0));
        assertEquals(39, Sa818Tones.toModule(2, 0));                                       // D023N
        assertEquals(121, Sa818Tones.toModule(2, 82));                                     // D754N
        assertEquals(-1, Sa818Tones.toModule(3, 0));                                       // inverted DCS: no code on this module
        for (int code : new int[] { 0, 1, 38, 240, 252, 39, 121 }) {                       // round trip
            int[] t = Sa818Tones.fromModule(code);
            assertEquals("code " + code, code, Sa818Tones.toModule(t[0], t[1]));
        }
    }

    @Test public void theFactoryChannelIsWrittenAsTheStockAppWritesIt() {
        // CHANNEL_INFO row 60: GBW 1, TFV/RFV 435.025, tone 240 both ways, SQ 3, POWER 1
        ChannelStore.Channel ch = ChannelStore.flyscaleChannel(60, "1", "435.025", "435.025", "240", "240", "3", "1");
        assertNotNull(ch);
        assertEquals("CH 61", ch.name); assertEquals(435_025_000, ch.rxFreq); assertEquals(435_025_000, ch.txFreq);
        assertEquals(1, ch.band); assertEquals(1, ch.power); assertEquals(3, ch.sq);
        assertEquals(1, ch.rxType); assertEquals(62.5, ToneTable.ctcssHz(ch.rxSubCode), 0.01);
        int[] g = { ch.band, ch.rxFreq, ch.txFreq, Sa818Tones.toModule(ch.txType, ch.txSubCode), Sa818Tones.toModule(ch.rxType, ch.rxSubCode), ch.sq, ch.power };
        assertEquals("AT+DMOSETGROUP=1,435.025,435.025,240,240,3,1", AtModuleEngine.groupCommand(g, ch.sq));   // sniffed from the stock app
        assertNull(ChannelStore.flyscaleChannel(1, "1", "", "abc", "0", "0", "3", "1"));
    }

    @Test public void frequenciesAndRange() {
        assertEquals("435.025", AtModuleEngine.mhz(435_025_000));
        assertEquals("435.0125", AtModuleEngine.mhz(435_012_500));
        assertEquals("446.000", AtModuleEngine.mhz(446_000_000));
        assertTrue(AtModuleEngine.inBand(400_000_000) && AtModuleEngine.inBand(470_000_000));
        assertFalse(AtModuleEngine.inBand(399_995_000) || AtModuleEngine.inBand(470_005_000) || AtModuleEngine.inBand(146_520_000));
        // transmit and receive in the stock order: tx first
        assertEquals("AT+DMOSETGROUP=0,440.000,445.000,0,12,0,0", AtModuleEngine.groupCommand(new int[] { 0, 445_000_000, 440_000_000, 0, 12, 5, 0 }, 0));
    }

    @Test public void repliesAsTheModuleSendsThem() {
        assertArrayEquals(new String[] { "CONNECT", "0" }, AtModuleEngine.reply("+DMOCONNECT:0"));
        assertArrayEquals(new String[] { "VERQ", "PB202EU05" }, AtModuleEngine.reply("+DMOVERQ:PB202EU05"));
        assertArrayEquals(new String[] { "SETGROUP", "1" }, AtModuleEngine.reply("+DMOSETGROUP:1"));
        assertArrayEquals(new String[] { "RSSI", "123" }, AtModuleEngine.reply("RSSI=123"));
        assertNull(AtModuleEngine.reply("AT+DMOCONNEC:1"));                                  // its error echo is not an answer
        assertNull(AtModuleEngine.reply(""));
        assertEquals(RadioEngine.CMD_SET_ANALOG, AtModuleEngine.statsKey("SETGROUP"));
        assertEquals(RadioEngine.CMD_VERSION, AtModuleEngine.statsKey("VERQ"));
        assertEquals(4, AtModuleEngine.moduleVolumeFor(8));
        assertEquals(1, AtModuleEngine.moduleVolumeFor(0));
    }

    @Test public void theW6IsDetected() {
        assertSame(DeviceProfile.ANYSECU_W6, DeviceProfile.detectBuiltIn("unisoc w6+ sl8541e_3h10_32b", false, false));
        assertSame(DeviceProfile.UNKNOWN, DeviceProfile.detectBuiltIn("someone w6 phone", false, false));
        DeviceProfile w6 = DeviceProfile.ANYSECU_W6;
        assertEquals("sa818", w6.protocol); assertFalse(w6.verified);                       // never transmits until tested on air
        assertEquals(DeviceProfile.Audio.CAMCORDER, w6.audio); assertEquals(9600, w6.baud);
        assertEquals("sa818", w6.copy(true, false).protocol);                               // an advisory's copy keeps the protocol and lines
        assertSame(w6.rxLinesOn, w6.copy(true, false).rxLinesOn);
        assertFalse(w6.dmr());                                                              // the DMR pages and choices are hidden
        assertFalse(w6.hasMessages() || w6.hasContacts());                                 // until a text mode works on it
        assertTrue(DeviceProfile.ARMOR_26_ULTRA.hasMessages() && DeviceProfile.ARMOR_26_ULTRA.hasContacts());
        assertTrue(DeviceProfile.ARMOR_26_ULTRA.dmr() && DeviceProfile.XPLORE_1.dmr() && DeviceProfile.UNKNOWN.dmr());
    }

    @Test public void receiveAudioFramesAreStereo() {
        short[] m = { 0x0102, (short) 0xFFFE };
        byte[] out = new byte[8];
        assertEquals(8, CamcorderAudio.toStereo(m, 2, out, 1));
        assertArrayEquals(new byte[] { 0x02, 0x01, 0x02, 0x01, (byte) 0xFE, (byte) 0xFF, (byte) 0xFE, (byte) 0xFF }, out);
        CamcorderAudio.toStereo(new short[] { 100, 5000, -5000 }, 3, new byte[12], 12);
        byte[] g = new byte[12]; CamcorderAudio.toStereo(new short[] { 100, 5000, -5000 }, 3, g);   // the fixed gain, clipped at full scale
        java.nio.ShortBuffer sb = java.nio.ByteBuffer.wrap(g).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        assertEquals(1200, sb.get(0)); assertEquals(1200, sb.get(1)); assertEquals(32767, sb.get(2)); assertEquals(-32768, sb.get(4));
    }

    /** The stock app's media-volume to module-volume bands (0-25: 0 = speaker off, 1-3 = 1, 4-6 = 2 ... 22-25 = 8). */
    @Test public void speakerFollowsMediaVolumeLikeTheStockApp() {
        int[] want = new int[26];
        for (int v = 1; v <= 25; v++) want[v] = v < 4 ? 1 : v < 7 ? 2 : v < 10 ? 3 : v < 13 ? 4 : v < 16 ? 5 : v < 19 ? 6 : v < 22 ? 7 : 8;
        for (int v = 0; v <= 25; v++) assertEquals("media " + v, want[v], AtModuleEngine.speakerLevelFor(v, 25));
        assertEquals(8, AtModuleEngine.speakerLevelFor(15, 15)); assertEquals(1, AtModuleEngine.speakerLevelFor(1, 15)); assertEquals(0, AtModuleEngine.speakerLevelFor(3, 0));
        assertNotNull(DeviceProfile.ANYSECU_W6.muteLines); assertNull(DeviceProfile.ARMOR_26_ULTRA.muteLines);
        assertArrayEquals(DeviceProfile.ANYSECU_W6.muteLines, DeviceProfile.ANYSECU_W6.copy(true, false).muteLines);
    }
}
