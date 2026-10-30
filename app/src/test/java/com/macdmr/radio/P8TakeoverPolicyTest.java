/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8TakeoverPolicyTest {
 @Test public void targetThenConfirmOnlyOnce(){P8TakeoverPolicy p=new P8TakeoverPolicy(100);assertEquals(P8TakeoverPolicy.Action.STOP,p.next(101,true,true,true,false,true,false));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(102,true,true,true,false,true,false));assertEquals(P8TakeoverPolicy.Action.CONFIRM,p.next(103,true,false,false,true,true,false));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(104,true,false,false,true,true,false));assertEquals(P8TakeoverPolicy.Action.FINISH,p.next(105,true,true,false,false,true,true));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(106,true,true,true,true,true,true));}
 @Test public void unrelatedPageCannotClick(){P8TakeoverPolicy p=new P8TakeoverPolicy(0);assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(1,true,false,true,true,true,false));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(2,false,true,true,true,true,false));}
 @Test public void assertedOrUnknownPttStopsBeforeClick(){P8TakeoverPolicy p=new P8TakeoverPolicy(0);assertEquals(P8TakeoverPolicy.Action.ABORT,p.next(1,true,true,true,false,false,false));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(2,true,true,true,true,true,false));}
 @Test public void timeoutCannotRetry(){P8TakeoverPolicy p=new P8TakeoverPolicy(0);assertEquals(P8TakeoverPolicy.Action.ABORT,p.next(12000,true,true,true,false,true,false));assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(12001,true,true,true,true,true,true));}
 @Test public void confirmationRequiresPrecedingTargetClick(){P8TakeoverPolicy p=new P8TakeoverPolicy(10);assertEquals(P8TakeoverPolicy.Action.WAIT,p.next(11,true,false,false,true,true,false));}
 @Test public void stoppedCanFinishWithoutClick(){P8TakeoverPolicy p=new P8TakeoverPolicy(10);assertEquals(P8TakeoverPolicy.Action.FINISH,p.next(11,false,false,false,false,true,true));}
}
