package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8TxStatusTest {
    @Test public void stockAcknowledgementGolden(){assertEquals("68 36 01 01 95 B7 00 01 01 10",Frame.hex(P8TxStatus.acknowledgement(),10));}
    @Test public void duplicateEndAllowedOnlyAfterOwnedKey(){P8TxStatus s=new P8TxStatus();s.keyed(10);assertTrue(s.status(3,11));assertFalse(s.status(4,12));s.released(13);assertFalse(s.status(4,14));assertFalse(s.status(4,1013));}
    @Test(expected=IllegalArgumentException.class)public void unsolicitedEndStillStops(){new P8TxStatus().status(4,100);}
    @Test(expected=IllegalArgumentException.class)public void lateEndStillStops(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(2);s.status(4,1003);}
    @Test(expected=IllegalArgumentException.class)public void startAfterReleaseStillStops(){P8TxStatus s=new P8TxStatus();s.keyed(1);s.released(2);s.status(3,3);}
}
