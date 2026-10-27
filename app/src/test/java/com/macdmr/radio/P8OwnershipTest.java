package com.macdmr.radio;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class P8OwnershipTest {
    private P8Ownership.Evidence e(P8Ownership.StockState state, boolean absent, boolean visible, int holders, int ptt, long at) {
        return new P8Ownership.Evidence(state,absent,visible,holders,ptt,at);
    }
    @Test public void currentObservationBlocksWithoutTouchingBackend() throws Exception {
        final int[] calls={0};
        try { P8OpenProbe.execute(e(P8Ownership.StockState.RUNNING,false,false,-1,1,100),101,true,()->{calls[0]++;return "bad";}); fail(); }
        catch(IOException expected){assertTrue(expected.getMessage().contains("Stock"));}
        assertEquals(0,calls[0]);
    }
    @Test public void incompleteVisibilityNeverMeansFreeEvenIfStockIsStopped() {
        assertTrue(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.STOPPED,true,false,0,1,100),101,true).contains("incomplete"));
    }
    @Test public void approvalTimeHoldersAndPttAreIndependentRequirements() {
        P8Ownership.Evidence good=e(P8Ownership.StockState.STOPPED,true,true,0,1,100);
        assertNotNull(P8Ownership.openProbeBlockReason(good,101,false));
        assertNotNull(P8Ownership.openProbeBlockReason(good,99,true));
        assertNotNull(P8Ownership.openProbeBlockReason(good,5101,true));
        assertNotNull(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.UNKNOWN,true,true,0,1,100),101,true));
        assertNotNull(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.STOPPED,false,true,0,1,100),101,true));
        assertNotNull(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.STOPPED,true,true,1,1,100),101,true));
        assertNotNull(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.STOPPED,true,true,-1,1,100),101,true));
        for(int ptt:new int[]{-1,0,2}) assertNotNull(P8Ownership.openProbeBlockReason(e(P8Ownership.StockState.STOPPED,true,true,0,ptt,100),101,true));
        assertNull(P8Ownership.openProbeBlockReason(good,101,true));
    }
    @Test public void reviewedPreparedOperationCallsExactlyOnceAndNeverRetries() throws Exception {
        final int[] calls={0};
        String result=P8OpenProbe.execute(e(P8Ownership.StockState.STOPPED,true,true,0,1,100),101,true,()->{calls[0]++;return "FAIL open errno=13";});
        assertEquals("FAIL open errno=13",result); assertEquals(1,calls[0]);
    }
    @Test public void shippedBuildCannotReachNativeProbeEvenWithInventedEvidence() {
        assertFalse(BuildConfig.P8_OPEN_PROBE_REVIEWED);
        try { P8OpenProbe.runReviewed(e(P8Ownership.StockState.STOPPED,true,true,0,1,100),101); fail(); }
        catch(IOException expected){assertTrue(expected.getMessage().contains("disabled in this build"));}
    }
}
