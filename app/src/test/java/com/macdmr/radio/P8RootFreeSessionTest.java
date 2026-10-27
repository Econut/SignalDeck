package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8RootFreeSessionTest {
    @Test public void refusesTxUnknownAndStaleEvidence() {
        assertNotNull(P8RootFreeSession.blockReason(false,true,true,1,1,0));
        assertNotNull(P8RootFreeSession.blockReason(true,false,true,1,1,0));
        assertNotNull(P8RootFreeSession.blockReason(true,true,false,1,1,0));
        for(long age:new long[]{-1,5001}) assertNotNull(P8RootFreeSession.blockReason(true,true,true,age,1,0));
        for(int ptt:new int[]{-1,0,2}) assertNotNull(P8RootFreeSession.blockReason(true,true,true,1,ptt,0));
        for(int mode:new int[]{-1,3,0x26}) assertNotNull(P8RootFreeSession.blockReason(true,true,true,1,1,mode));
        assertNull(P8RootFreeSession.blockReason(true,true,true,1,1,0));
        assertNull(P8RootFreeSession.blockReason(true,true,true,1,1,1));
        assertNull(P8RootFreeSession.blockReason(true,true,true,1,1,2));
    }
}
