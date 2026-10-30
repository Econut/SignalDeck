/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import org.junit.Test;import static org.junit.Assert.*;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.util.Arrays;
public class P8ModuleInitTest {
 static byte[] ack(int cmd){return new Frame(cmd,0,0,new byte[0]).encode();}
 static byte[] version(String s){return new Frame(0x34,0,0,s.getBytes(StandardCharsets.US_ASCII)).encode();}
 static final String VERSION="DMR003BN.UV4P.V026";
 static class Fake implements P8ModuleInit.Transport {
  int writes,guards;boolean reject,failGuard;String v=VERSION;
  public void guard()throws Exception{guards++;if(failGuard)throw new IOException("ownership");}
  public byte[] exchange(byte[] packet)throws Exception{writes++;assertEquals(writes==1?0x27:0x34,packet[1]&255);assertEquals(1,packet[8]);if(reject)throw new IOException("transport");return writes==1?ack(0x27):version(v);}
 }
 @Test public void exactStockPacket(){assertEquals("68 27 01 01 95 C6 00 01 01 10",Frame.hex(P8ModuleInit.request(),10));}
 @Test public void initThenVersionGuardsEveryStage()throws Exception{Fake f=new Fake();assertEquals(VERSION,P8ModuleInit.run(f,x->{}));assertEquals(2,f.writes);assertEquals(3,f.guards);}
 @Test public void noWriteWithoutOwnership()throws Exception{Fake f=new Fake();f.failGuard=true;try{P8ModuleInit.run(f,x->{});fail();}catch(IOException expected){}assertEquals(0,f.writes);}
 @Test public void noRetryAfterTransportError()throws Exception{Fake f=new Fake();f.reject=true;try{P8ModuleInit.run(f,x->{});fail();}catch(IOException expected){}assertEquals(1,f.writes);}
 @Test public void differentFirmwareRefused()throws Exception{Fake f=new Fake();f.v="DMR003BN.other";try{P8ModuleInit.run(f,x->{});fail();}catch(IOException expected){}assertEquals(2,f.writes);}
 @Test public void checksumRejected()throws Exception{byte[] b=ack(0x27);b[4]^=1;reject(b);}
 @Test public void truncatedRejected()throws Exception{reject(Arrays.copyOf(ack(0x27),8));}
 @Test public void emptyRejected()throws Exception{reject(new byte[0]);}
 @Test public void negativeAckRejected()throws Exception{reject(new Frame(0x27,0,1,new byte[0]).encode());}
 @Test public void unexpectedInitBodyRejected()throws Exception{reject(new Frame(0x27,0,0,new byte[]{1}).encode());}
 @Test public void duplicateRejected()throws Exception{reject(join(ack(0x27),ack(0x27)));}
 @Test public void unrelatedCommandRejected()throws Exception{reject(ack(0x34));}
 @Test public void transmitStatusRejected()throws Exception{reject(join(new Frame(0x36,2,0,new byte[]{3}).encode(),ack(0x27)));}
 @Test public void receiveStatusForwarded()throws Exception{int[] n={0};P8ModuleInit.reply(join(new Frame(0x36,2,0,new byte[]{1}).encode(),ack(0x27)),0x27,x->n[0]++);assertEquals(1,n[0]);}
 @Test public void knownPowerOnBannerAllowed()throws Exception{byte[] banner={(byte)0xbf,(byte)0xaa,(byte)0xbb,(byte)0xfa,(byte)0xb3,(byte)0xf5,(byte)0xca,(byte)0xbc,(byte)0xbb,(byte)0xaf,(byte)0xcd,(byte)0xea,(byte)0xb3,(byte)0xc9,13,10,0};assertEquals(0x27,P8ModuleInit.reply(join(banner,ack(0x27)),0x27,x->{}).cmd);}
 static byte[] join(byte[] a,byte[] b){byte[] c=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,c,a.length,b.length);return c;}
 static void reject(byte[] b)throws Exception{try{P8ModuleInit.reply(b,0x27,x->{});fail();}catch(IOException expected){}}
}
