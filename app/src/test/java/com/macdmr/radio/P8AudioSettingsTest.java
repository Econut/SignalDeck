package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8AudioSettingsTest {
    @Test public void currentGainPolicyIsIndependentOfDspCapacity(){for(int n:new int[]{0,5,16})try{P8AudioSettings.gain(n);fail();}catch(IllegalArgumentException expected){}P8AudioSettings.gain(4);byte[] b={1,0};P8RxProtocol.amplify(b,0,2,16);assertEquals(16,b[0]);}
    @Test public void moduleCommandHasVerifiedWireShape(){assertEquals("68 2E 01 01 95 BF 00 01 01 10",Frame.hex(P8Protocol.receiveVolume(1).encode(),10));assertEquals("68 2E 01 01 8D BF 00 01 09 10",Frame.hex(P8Protocol.receiveVolume(9).encode(),10));for(int n:new int[]{0,10,255})try{P8AudioSettings.module(n);fail();}catch(IllegalArgumentException expected){}}
    @Test public void volumeAckMustHaveMatchingCommandAndSuccessfulStatus(){P8RxProtocol.requireAck(new P8RxProtocol.Stream(),new Frame(0x2e,0,0,null).encode(),f->{},0x2e);
        for(Frame f:new Frame[]{new Frame(0x23,0,0,null),new Frame(0x2e,0,1,null)})try{P8RxProtocol.requireAck(new P8RxProtocol.Stream(),f.encode(),x->{},0x2e);fail();}catch(IllegalArgumentException expected){}}
    @Test public void meterHasKnownDbScaleAndWindowResetRetainsSessionClips(){P8AudioSettings.Meter m=new P8AudioSettings.Meter();assertEquals(-96,m.rmsDb(),0);m.feed(new byte[]{0,0x40},0,2,4);assertEquals(-6.0206,m.rmsDb(),0.001);assertEquals(-6.0206,m.peakDb(),0.001);assertEquals(1,m.clips());m.resetWindow();assertEquals(-96,m.peakDb(),0);assertEquals(1,m.clips());m.feed(new byte[]{1,0},0,2,1);assertEquals(0,m.lastClips());}
}
