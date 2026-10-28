package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8TxStatusTest {
    @Test public void stockAcknowledgementGolden(){assertEquals("68 36 01 01 95 B7 00 01 01 10",Frame.hex(P8TxStatus.acknowledgement(),10));}
    @Test public void duplicateEndAllowedOnlyAfterOwnedKey(){P8TxStatus s=new P8TxStatus();s.keyed(10);assertTrue(s.status(3,11));assertFalse(s.status(4,12));s.released(13);assertFalse(s.status(4,14));assertFalse(s.status(4,1013));}
    @Test(expected=IllegalArgumentException.class)public void unsolicitedEndStillStops(){new P8TxStatus().status(4,100);}
    @Test(expected=IllegalArgumentException.class)public void lateEndStillStops(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(2);s.status(4,1003);}
    @Test public void observedShortPressLateStartRemainsReleased(){P8TxStatus s=new P8TxStatus();s.keyed(435);s.released(493);assertFalse(s.status(3,699));assertFalse(s.status(4,750));}
    @Test(expected=IllegalArgumentException.class)public void unsolicitedStartStillStops(){new P8TxStatus().status(3,100);}
    @Test(expected=IllegalArgumentException.class)public void lateStartStillStops(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(2);s.status(3,1003);}
    @Test(expected=IllegalArgumentException.class)public void notificationsNeverExtendGraceWindow(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(2);assertFalse(s.status(3,1002));assertFalse(s.status(4,1002));s.status(3,1003);}
    @Test(expected=IllegalArgumentException.class)public void timeBeforeReleaseCannotBeAccepted(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(20);s.status(3,19);}
    @Test public void newPressHasItsOwnOwnedState(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(20);assertFalse(s.status(3,30));s.keyed(40);assertTrue(s.status(3,50));s.released(60);assertFalse(s.status(4,70));}
}
