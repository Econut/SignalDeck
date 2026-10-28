package com.macdmr.radio;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

/** What a radio can do, from its profile: bands, signal readings, the symbol engine. */
public class RadioCapsTest {
    @Test public void armorBandsAreTheModulesMeasuredLimits() {
        DeviceProfile a = DeviceProfile.ARMOR_26_ULTRA;
        assertTrue(a.tunable(136_000_000L)); assertTrue(a.tunable(174_000_000L)); assertTrue(a.tunable(480_000_000L));
        assertFalse(a.tunable(174_005_000L)); assertFalse(a.tunable(220_000_000L)); assertFalse(a.tunable(399_995_000L));
        assertEquals("136–174 and 400–480 MHz", a.bandsText());
        assertTrue(a.rssi); assertTrue(a.symbolEngine());
    }

    @Test public void w6IsUhfOnlyWithoutSignalReadings() {
        DeviceProfile w = DeviceProfile.ANYSECU_W6;
        assertFalse(w.tunable(PacketEngine.APRS_FREQ_HZ)); assertFalse(w.tunable(PacketEngine.SSTV_FREQ_HZ)); assertFalse(w.tunable(162_400_000L));
        assertTrue(w.tunable(437_825_000L)); assertTrue(w.tunable(470_000_000L)); assertFalse(w.tunable(470_005_000L));
        assertEquals("400–470 MHz", w.bandsText());
        assertFalse(w.rssi); assertFalse(w.symbolEngine());
        DeviceProfile c = w.copy(true, false);                              // a copy keeps them
        assertArrayEquals(w.bandsHz, c.bandsHz); assertFalse(c.rssi);
    }

    @Test public void nextTunableJumpsTheGap() {
        DeviceProfile a = DeviceProfile.ARMOR_26_ULTRA;
        assertEquals(400_000_000L, a.nextTunable(174_012_500L, 1));
        assertEquals(174_000_000L, a.nextTunable(399_987_500L, -1));
        assertEquals(146_520_000L, a.nextTunable(146_520_000L, 1));
        assertEquals(-1L, a.nextTunable(480_012_500L, 1));                   // nothing above the top band
        assertEquals(-1L, DeviceProfile.ANYSECU_W6.nextTunable(399_000_000L, -1));
    }

    @Test public void remoteProfilesMaySetThem() throws Exception {
        JSONObject o = new JSONObject().put("name", "Test").put("controlPort", "/dev/ttyS9").put("baud", 9600).put("audio", "CAMCORDER")
                .put("id", "test-phone").put("revision", 1).put("protocol", "sa818")
                .put("bandsHz", new org.json.JSONArray().put(136_000_000L).put(174_000_000L)).put("rssi", false);
        DeviceProfile p = DeviceProfile.fromJson(o);
        assertTrue(p.tunable(146_520_000L)); assertFalse(p.tunable(446_000_000L)); assertFalse(p.rssi);
        DeviceProfile d = DeviceProfile.fromJson(new JSONObject(o.toString()).put("bandsHz", JSONObject.NULL).put("rssi", JSONObject.NULL));
        assertTrue("default: the HR-C7000 module's bands", d.tunable(446_000_000L) && d.tunable(146_520_000L)); assertTrue(d.rssi);
        try { DeviceProfile.fromJson(new JSONObject(o.toString()).put("bandsHz", new org.json.JSONArray().put(1L).put(2L).put(3L))); fail("odd list"); } catch (org.json.JSONException expected) { }
        try { DeviceProfile.fromJson(new JSONObject(o.toString()).put("bandsHz", new org.json.JSONArray().put(5L).put(2L))); fail("low above high"); } catch (org.json.JSONException expected) { }
    }
}
