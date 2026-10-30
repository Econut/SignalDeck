/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 initialization; see COPYRIGHT. */
package com.macdmr.radio;
import java.io.IOException;
import java.util.Arrays;
/** Fixed stock runtime initialization, not a reset, firmware update or calibration operation. */
public final class P8ModuleInit {
 private P8ModuleInit(){}
 public interface Transport {void guard()throws Exception;byte[] exchange(byte[] packet)throws Exception;}
 private static final byte[] BANNER={(byte)0xbf,(byte)0xaa,(byte)0xbb,(byte)0xfa,(byte)0xb3,(byte)0xf5,(byte)0xca,(byte)0xbc,(byte)0xbb,(byte)0xaf,(byte)0xcd,(byte)0xea,(byte)0xb3,(byte)0xc9,13,10,0};
 public static byte[] request(){return Frame.request(0x27,(byte)1).encode();}
 public static String run(Transport t,P8RxProtocol.Sink events)throws Exception{
  t.guard();reply(t.exchange(request()),0x27,events);
  t.guard();String version=P8Protocol.versionText(reply(t.exchange(P8Protocol.versionQuery().encode()),0x34,events));
  if(!"DMR003BN.UV4P.V026".equals(version))throw new IOException("Unverified P8 module version: "+version+"; initialization stopped");
  t.guard();return version;
 }
 static Frame reply(byte[] capture,int command,P8RxProtocol.Sink events)throws IOException{
  int off=0;
  if(capture.length>=BANNER.length && Arrays.equals(Arrays.copyOf(capture,BANNER.length),BANNER))off=BANNER.length;
  Frame answer=null;
  while(off<capture.length){
   Frame[] f=new Frame[1];int n=Frame.tryDecode(capture,off,capture.length-off,f);
   if(n<=0)throw new IOException("Invalid or incomplete initialization response");off+=n;
   Frame x=f[0];
   if(x.cmd==0x36 && x.body.length==1 && (x.body[0]==3 || x.body[0]==4))throw new IOException("Unexpected transmit status during initialization");
   if(x.cmd==command){if(answer!=null || x.rw!=0 || x.sr!=0)throw new IOException("Rejected or duplicate initialization acknowledgement");answer=x;}
   else if(x.cmd==0x36 && x.rw==2 && x.sr==0 && x.body.length==1)events.frame(x);
   else throw new IOException("Unexpected initialization response command");
  }
  if(answer!=null && command==0x27 && answer.body.length!=0)throw new IOException("Unexpected initialization acknowledgement body");
  if(answer==null)throw new IOException("No successful initialization acknowledgement for 0x"+Integer.toHexString(command));
  return answer;
 }
}
