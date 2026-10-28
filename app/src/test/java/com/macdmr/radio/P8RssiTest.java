package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8RssiTest {
    private Frame value(int raw){return new Frame(0x32,0,0,new byte[]{(byte)raw});}
    @Test public void fixedReadQueryAndFamilyConversion(){assertEquals("68 32 01 01 95 BB 00 01 01 10",Frame.hex(P8Rssi.query(),10));assertEquals(-100,P8Rssi.dbm(40),0);assertEquals(-99,P8Rssi.dbm(42),0);assertEquals(-20,P8Rssi.dbm(200),0);for(int raw:new int[]{-1,0,201,226,255,256})assertEquals(P8Rssi.UNKNOWN,P8Rssi.dbm(raw),0);}
    @Test public void firstProbeNeedsSettledReceptionThenPollingMayReadFloor(){P8Rssi.Poll poll=new P8Rssi.Poll();poll.tuned(0);assertFalse(poll.queryDue(799,true,false));assertFalse(poll.queryDue(800,false,false));assertTrue(poll.queryDue(800,true,false));assertFalse(poll.queryDue(900,true,false));assertEquals(-100,poll.reply(value(40),950),0);assertFalse(poll.queryDue(1799,true,false));assertTrue(poll.queryDue(1800,false,false));}
    @Test public void unansweredProbeStopsWithoutRetryEvenAfterRetune(){P8Rssi.Poll poll=new P8Rssi.Poll();poll.tuned(0);assertTrue(poll.queryDue(800,true,false));assertFalse(poll.timedOut(2299));assertTrue(poll.timedOut(2300));poll.tuned(5000);assertFalse(poll.queryDue(9000,true,false));assertEquals(P8Rssi.UNKNOWN,poll.reply(value(40),9001),0);}
    @Test public void invalidFirstReadingOrRejectedReplyDoesNotEnablePolling(){for(Frame f:new Frame[]{value(0),value(226),new Frame(0x32,0,1,new byte[]{40}),new Frame(0x32,2,0,new byte[]{40}),new Frame(0x32,0,0,new byte[2])}){P8Rssi.Poll p=new P8Rssi.Poll();p.tuned(0);p.queryDue(800,true,false);assertEquals(P8Rssi.UNKNOWN,p.reply(f,900),0);assertTrue(p.disabled());assertFalse(p.queryDue(10000,true,false));}}
    @Test public void busySessionNeverQueriesAndPauseCancelsOutstanding(){P8Rssi.Poll p=new P8Rssi.Poll();p.tuned(0);assertFalse(p.queryDue(800,true,true));assertTrue(p.queryDue(900,true,false));p.pause(901);assertFalse(p.waiting());assertFalse(p.timedOut(9000));assertFalse(p.queryDue(1700,true,false));}
    @Test public void lateReplyCannotBeatTimeoutCheck(){P8Rssi.Poll p=new P8Rssi.Poll();p.tuned(0);p.queryDue(800,true,false);assertEquals(P8Rssi.UNKNOWN,p.reply(value(40),2300),0);assertTrue(p.disabled());}
    @Test public void staleReadingsExpireAndUnsolicitedRepliesDoNotEnable(){P8Rssi.Poll p=new P8Rssi.Poll();assertEquals(P8Rssi.UNKNOWN,p.reply(value(40),1),0);p.tuned(0);p.queryDue(800,true,false);p.reply(value(40),900);assertFalse(p.stale(3899));assertTrue(p.stale(3900));assertFalse(p.stale(4000));}
}
