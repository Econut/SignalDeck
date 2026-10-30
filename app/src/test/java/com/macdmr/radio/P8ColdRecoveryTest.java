/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;import static org.junit.Assert.*;
public class P8ColdRecoveryTest {
 static class Fake implements P8ColdRecovery.Backend {boolean closed=true,fail;int ptt=0,power=1,audio=1,writes;public boolean closed(){return closed;}public int state(String p){return p.equals(P8Protocol.PTT_NODE)?ptt:p.equals(P8Protocol.POWER_NODE)?power:audio;}public void release(){writes++;if(!fail)ptt=1;}}
 private void blocked(Fake f,boolean approval){try{P8ColdRecovery.release(f,approval);fail("Expected refusal");}catch(Exception expected){}}
 @Test public void releaseOnce(){Fake f=new Fake();try{P8ColdRecovery.release(f,true);}catch(Exception e){throw new AssertionError(e);}assertEquals(1,f.writes);assertEquals(1,f.ptt);}
 @Test public void alreadyReleasedDoesNotWrite()throws Exception{Fake f=new Fake();f.ptt=1;P8ColdRecovery.release(f,true);assertEquals(0,f.writes);}
 @Test public void explicitOptInRequired(){Fake f=new Fake();blocked(f,false);assertEquals(0,f.writes);}
 @Test public void activeSessionRefuses(){Fake f=new Fake();f.closed=false;blocked(f,true);assertEquals(0,f.writes);}
 @Test public void unexpectedPowerRefuses(){Fake f=new Fake();f.power=0;blocked(f,true);assertEquals(0,f.writes);}
 @Test public void unexpectedAudioRefuses(){Fake f=new Fake();f.audio=4;blocked(f,true);assertEquals(0,f.writes);}
 @Test public void unknownPttRefuses(){Fake f=new Fake();f.ptt=-1;blocked(f,true);assertEquals(0,f.writes);}
 @Test public void failedReleaseDoesNotRetry(){Fake f=new Fake();f.fail=true;blocked(f,true);assertEquals(1,f.writes);}
}
