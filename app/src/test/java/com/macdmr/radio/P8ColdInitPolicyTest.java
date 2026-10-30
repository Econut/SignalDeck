/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;import static org.junit.Assert.*;
public class P8ColdInitPolicyTest {
 @Test public void freshResetRequired(){P8ColdInitPolicy p=new P8ColdInitPolicy(0);assertEquals(P8ColdInitPolicy.Result.WAIT,p.observe(1,1,true));assertEquals(P8ColdInitPolicy.Result.WAIT,p.observe(2,0,true));assertEquals(P8ColdInitPolicy.Result.READY,p.observe(3,1,true));}
 @Test public void noMarkerTimesOut(){P8ColdInitPolicy p=new P8ColdInitPolicy(0);assertEquals(P8ColdInitPolicy.Result.FAILED,p.observe(15000,1,true));}
 @Test public void unexpectedStateFails(){P8ColdInitPolicy p=new P8ColdInitPolicy(0);assertEquals(P8ColdInitPolicy.Result.FAILED,p.observe(1,0,false));assertEquals(P8ColdInitPolicy.Result.FAILED,p.observe(2,1,true));}
 @Test public void staleClockFails(){assertEquals(P8ColdInitPolicy.Result.FAILED,new P8ColdInitPolicy(5).observe(4,0,true));}
}
