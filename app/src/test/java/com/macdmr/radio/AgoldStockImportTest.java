package com.macdmr.radio;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Rows from the Unihertz Atom XL stock app's intercom.db (com.agold.intercom, read 2026-10-29) as SignalDeck channels. */
public class AgoldStockImportTest {
    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test public void dmrChannelFromTheDefaultArea() {
        ChannelStore.Channel ch = ChannelStore.agoldChannel(row("area", "1", "num", "1", "type", "0", "power", "1", "sendfreq", "401025000",
                "recvfreq", "401025000", "deviceId", "1", "colorCode", "1", "contactType", "2", "contactNum", "1", "encryptSwitch", "255",
                "inboundSlot", "0", "outboundSlot", "0", "channelMode", "0"), 1, "Default");
        assertNotNull(ch);
        assertTrue(ch.isDigital());
        assertEquals(401_025_000, ch.rxFreq); assertEquals(401_025_000, ch.txFreq);
        assertEquals("Default 1", ch.name);                   // the stock app leaves names empty: area name and number
        assertEquals(1, ch.contactType);                       // stock 2 = group, sent to the module as 1 (contactType - 1)
        assertEquals(1, ch.txContact);
        assertArrayEquals(new int[] { 1 }, ch.groups);         // no contactList: the group call's own talkgroup
        assertEquals(2, ch.encryptSw);                         // 255 = off
        assertEquals(1, ch.cc); assertEquals(1, ch.power);
    }

    @Test public void analogChannelsWideAndNarrowWithTones() {
        ChannelStore.Channel wide = ChannelStore.agoldChannel(row("num", "9", "type", "1", "power", "1", "sendfreq", "409025000", "recvfreq", "409025000",
                "band", "128", "squelchLevel", "2", "recvSubAudioType", "1", "recvCtcssFreq", "0", "sendAubAudioType", "1", "sendCtcssFreq", "0"), 9, "Default");
        assertFalse(wide.isDigital());
        assertEquals(1, wide.band);                            // band 128 = wide
        assertEquals(0, wide.rxType); assertEquals(0, wide.txType);   // sub-audio type 1 = none
        assertEquals(2, wide.sq);
        ChannelStore.Channel frs = ChannelStore.agoldChannel(row("num", "3", "type", "1", "power", "0", "sendfreq", "409775000", "recvfreq", "409775000",
                "band", "0", "squelchLevel", "2", "recvSubAudioType", "2", "recvCtcssFreq", "12", "sendAubAudioType", "3", "sendCtcssFreq", "23"), 3, "China FRS");
        assertEquals(0, frs.band); assertEquals(0, frs.power);
        assertEquals(1, frs.rxType); assertEquals(12, frs.rxSubCode);
        assertEquals(2, frs.txType); assertEquals(23, frs.txSubCode);
        assertEquals("China FRS 3", frs.name);
    }

    @Test public void contactListAllCallAndBadRows() {
        ChannelStore.Channel ch = ChannelStore.agoldChannel(row("num", "2", "type", "0", "recvfreq", "402025000", "sendfreq", "402025000",
                "contactType", "4", "contactNum", "9", "contactList", "91:92:0:0:91", "encryptSwitch", "1", "name", "Bench TG"), 2, "Default");
        assertEquals(0xFFFFFA, ch.txContact);                  // all call, as the stock app sends it
        assertEquals(3, ch.contactType);
        assertArrayEquals(new int[] { 91, 92 }, ch.groups);    // zeros and repeats dropped
        assertEquals(1, ch.encryptSw);
        assertEquals("Bench TG", ch.name);
        assertNull(ChannelStore.agoldChannel(row("num", "5", "type", "1", "recvfreq", null), 5, "Default"));   // no frequency
    }
}
