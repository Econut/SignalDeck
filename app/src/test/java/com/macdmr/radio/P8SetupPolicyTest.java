/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;import static org.junit.Assert.*;
public class P8SetupPolicyTest {
 @Test public void stockRequired(){assertEquals(P8SetupPolicy.Step.STOCK_MISSING,P8SetupPolicy.next(false,true,true,false,true));}
 @Test public void microphoneBeforeService(){assertEquals(P8SetupPolicy.Step.MICROPHONE,P8SetupPolicy.next(true,false,true,false,true));}
 @Test public void notificationsCanBeSkipped(){assertEquals(P8SetupPolicy.Step.NOTIFICATIONS,P8SetupPolicy.next(true,true,false,false,true));assertEquals(P8SetupPolicy.Step.CONNECT,P8SetupPolicy.next(true,true,false,true,true));}
 @Test public void helperMustActuallyConnect(){assertEquals(P8SetupPolicy.Step.HELPER,P8SetupPolicy.next(true,true,true,false,false));}
 @Test public void allChecksAllowConnect(){assertEquals(P8SetupPolicy.Step.CONNECT,P8SetupPolicy.next(true,true,true,false,true));}
 @Test public void savedFlagNotEnoughForComplete(){assertFalse(P8SetupPolicy.complete(true,true,false));assertFalse(P8SetupPolicy.complete(false,true,true));assertFalse(P8SetupPolicy.complete(true,false,true));assertTrue(P8SetupPolicy.complete(true,true,true));}
}
