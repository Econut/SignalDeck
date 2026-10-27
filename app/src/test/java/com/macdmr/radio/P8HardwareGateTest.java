package com.macdmr.radio;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class P8HardwareGateTest {
    @Test public void offlineEditionBlocksDirectHelpersBeforeAnyFileOrNativeAccess() throws Exception {
        if (!BuildConfig.P8_OFFLINE) return; // this test exercises the separately packaged edition
        java.nio.file.Path f = java.nio.file.Files.createTempFile("p8-offline-gate", ".txt");
        try {
            java.nio.file.Files.write(f, new byte[]{42});
            try { SysfsGpio.write(f.toString(), "0"); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("disabled")); }
            assertArrayEquals(new byte[]{42}, java.nio.file.Files.readAllBytes(f));
            assertNull(VendorSerial.openPort("/dev/ttysWK0", 57600));
            assertFalse(VendorSerial.load(null, "/does/not/exist.so"));
            assertTrue(RootSetup.run("must never execute").contains("root commands disabled"));
            assertTrue(new UartPcmAudio("/dev/ttyS0", 230400).startRx(false).contains("PCM port access disabled"));
        } finally { java.nio.file.Files.delete(f); }
    }
    interface Operation { void run() throws IOException; }
    @Test public void offlineProfileCannotOpenPowerSendOrDivertRawBytes() {
        RadioEngine e = new RadioEngine(DeviceProfile.UNIWA_P8, null);
        Operation[] operations = {e::open, e::powerOn, e::powerOff, e::powerCycle,
                () -> e.writeRaw(new byte[]{0x31}), () -> e.ptt(true), e::queryVersion,
                () -> e.setAnalog(145500000,145500000,1,0,1,0,0,0,0,1,2)};
        for (Operation op : operations) {
            try { op.run(); fail("hardware operation escaped the offline gate"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("offline-only")); }
        }
        assertFalse(e.isOpen());
    }
}
