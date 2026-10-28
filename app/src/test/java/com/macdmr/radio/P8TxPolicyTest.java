package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8TxPolicyTest {
    @Test public void unarmedNeverKeys(){assertFalse(new P8TxPolicy().consume(100,446106250,0x23));}
    @Test public void exactlyOneKeyPerArm(){P8TxPolicy p=new P8TxPolicy();p.arm(100);assertTrue(p.consume(101,446106250,0x23));assertFalse(p.consume(102,446106250,0x23));}
    @Test public void expirationAndChannelMismatchConsumeArm(){P8TxPolicy p=new P8TxPolicy();p.arm(100);assertFalse(p.consume(60100,446106250,0x23));p.arm(100);assertFalse(p.consume(101,446006250,0x23));assertFalse(p.consume(102,446106250,0x23));}
    @Test public void digitalCannotKey(){P8TxPolicy p=new P8TxPolicy();p.arm(100);assertFalse(p.consume(101,446106250,0x22));}
    @Test public void cancelDisarms(){P8TxPolicy p=new P8TxPolicy();p.arm(100);p.cancel();assertFalse(p.consume(101,446106250,0x23));assertEquals(10000,P8TxPolicy.MAX_KEY_MS);}
}
