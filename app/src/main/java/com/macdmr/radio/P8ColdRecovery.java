/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
import java.io.IOException;
/** Separately opted-in startup release only. Never keys, powers or opens a UART. */
public final class P8ColdRecovery {
 private P8ColdRecovery(){}
 public interface Backend {boolean closed();int state(String path)throws Exception;void release()throws Exception;}
 public static void release(Backend b,boolean approved)throws Exception{
  if(!approved)throw new IOException("Enable bounded cold-start recovery in P8 setup first");
  if(!b.closed())throw new IOException("Radio session/restore is not cleanly closed");
  if(b.state(P8Protocol.POWER_NODE)!=1 || b.state(P8Protocol.AUDIO_CONTROL_NODE)!=1)throw new IOException("Unexpected power/audio state; no write");
  int ptt=b.state(P8Protocol.PTT_NODE);if(ptt==1)return;
  if(ptt!=0)throw new IOException("Unknown PTT state; no write");
  if(!b.closed())throw new IOException("Session opened during recovery; no write");
  b.release();
  if(b.state(P8Protocol.PTT_NODE)!=1 || b.state(P8Protocol.POWER_NODE)!=1 || b.state(P8Protocol.AUDIO_CONTROL_NODE)!=1)throw new IOException("Startup release verification failed; no retry");
 }
}
