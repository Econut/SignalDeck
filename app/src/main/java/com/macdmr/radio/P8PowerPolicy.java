/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 power control; see COPYRIGHT. */
package com.macdmr.radio;
import java.io.IOException;
/** Stock enable/disable sequence only. Factory kernel show() returns constant1 for both nodes, not power telemetry. */
public final class P8PowerPolicy {
 private P8PowerPolicy(){}
 public interface Backend {boolean closed();boolean stockStopped()throws Exception;int state(String path)throws Exception;void power(boolean on)throws Exception;}
 public static void change(Backend b,boolean on)throws Exception{
  if(!b.closed() || !b.stockStopped() || b.state(P8Protocol.PTT_NODE)!=1)throw new IOException("Power change requires closed UARTs, stopped Intercom and released PTT");
  int p=b.state(P8Protocol.POWER_NODE),a=b.state(P8Protocol.AUDIO_CONTROL_NODE);
  if(p!=1 || a!=1)throw new IOException("Unknown power/audio combination; no write");
  if(!b.closed() || !b.stockStopped() || b.state(P8Protocol.PTT_NODE)!=1)throw new IOException("Ownership changed before power operation");
  b.power(on);
  if(b.state(P8Protocol.PTT_NODE)!=1 || b.state(P8Protocol.POWER_NODE)!=1 || b.state(P8Protocol.AUDIO_CONTROL_NODE)!=1)throw new IOException("Power verification failed; no retry");
 }
}
