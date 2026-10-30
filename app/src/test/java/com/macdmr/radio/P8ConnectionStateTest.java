package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8ConnectionStateTest {
    @Test public void phoneOnlyIsOffNotFault(){assertEquals(P8ConnectionState.OFF,P8ConnectionState.of(false,"phone-only",false));}
    @Test public void greenRequiresReady(){assertEquals(P8ConnectionState.READY,P8ConnectionState.of(true,"ready",false));assertEquals(P8ConnectionState.FAULT,P8ConnectionState.of(false,"P8 unsafe close",false));}
    @Test public void recoveryAndConnectingOverrideStaleReady(){assertEquals(P8ConnectionState.CONNECTING,P8ConnectionState.of(true,"ready",true));assertEquals(P8ConnectionState.CONNECTING,P8ConnectionState.of(false,"connecting P8",false));}
    @Test public void stoppedAndUnboundAreNotGreenOrFalselyCalledFault(){assertEquals(P8ConnectionState.DISCONNECTED,P8ConnectionState.of(false,"stopped",false));assertEquals(P8ConnectionState.DISCONNECTED,P8ConnectionState.of(false,null,false));}
}
