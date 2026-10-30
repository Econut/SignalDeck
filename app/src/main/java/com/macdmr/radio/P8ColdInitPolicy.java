/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
/** Stock startup must expose a fresh 0 -> 1 initialization marker, not a stale stored 1. */
public final class P8ColdInitPolicy {
 public enum Result { WAIT, READY, FAILED }
 private final long start;private boolean reset,finished;
 public P8ColdInitPolicy(long now){start=now;}
 public Result observe(long now,int marker,boolean released){
  if(finished)return Result.FAILED;
  if(!released || now<start || now-start>=15000){finished=true;return Result.FAILED;}
  if(marker==0)reset=true;
  if(reset && marker==1){finished=true;return Result.READY;}
  return Result.WAIT;
 }
}
