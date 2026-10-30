/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;import static org.junit.Assert.*;import java.io.IOException;
public class P8PowerPolicyTest {
 static class Fake implements P8PowerPolicy.Backend {boolean closed=true,stopped=true,fail,lastOn;int p=1,a=1,t=1,writes;
  public boolean closed(){return closed;}public boolean stockStopped(){return stopped;}
  public int state(String path){return path.equals(P8Protocol.PTT_NODE)?t:path.equals(P8Protocol.POWER_NODE)?p:a;}
  public void power(boolean on)throws Exception{writes++;if(fail)throw new IOException("write");lastOn=on;}
 }
 @Test public void stockEnableEvenIfNodesReadOn()throws Exception{Fake f=new Fake();P8PowerPolicy.change(f,true);assertEquals(1,f.writes);}
 @Test public void offThenOn()throws Exception{Fake f=new Fake();P8PowerPolicy.change(f,false);assertFalse(f.lastOn);assertEquals(1,f.p);assertEquals(1,f.a);P8PowerPolicy.change(f,true);assertTrue(f.lastOn);assertEquals(2,f.writes);}
 @Test public void unexpectedPowerReadRefused()throws Exception{Fake f=new Fake();f.p=0;reject(f);assertEquals(0,f.writes);}
 @Test public void liveUartRefused()throws Exception{Fake f=new Fake();f.closed=false;reject(f);assertEquals(0,f.writes);}
 @Test public void stockOwnershipRefused()throws Exception{Fake f=new Fake();f.stopped=false;reject(f);assertEquals(0,f.writes);}
 @Test public void assertedPttRefused()throws Exception{Fake f=new Fake();f.t=0;reject(f);assertEquals(0,f.writes);}
 @Test public void partialStateRefused()throws Exception{Fake f=new Fake();f.a=4;reject(f);assertEquals(0,f.writes);}
 @Test public void failedWriteNotRetried()throws Exception{Fake f=new Fake();f.fail=true;reject(f);assertEquals(1,f.writes);}
 @Test public void failedReadbackNotRetried()throws Exception{Fake f=new Fake(){public void power(boolean on){writes++;t=0;}};reject(f);assertEquals(1,f.writes);}
 static void reject(Fake f)throws Exception{try{P8PowerPolicy.change(f,false);fail();}catch(IOException expected){}}
}
