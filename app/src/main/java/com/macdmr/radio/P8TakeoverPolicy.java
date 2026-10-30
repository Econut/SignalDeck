/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
/** Single-use UI handover. No generic click engine, package argument, or command execution. */
public final class P8TakeoverPolicy {
    public enum Action { WAIT, STOP, CONFIRM, FINISH, ABORT }
    public static final long LIMIT_MS=12000;
    private final long start; private int stage;
    public P8TakeoverPolicy(long now){start=now;}
    public Action next(long now,boolean settings,boolean targetPage,boolean stopButton,boolean confirmDialog,boolean released,boolean stopped){
        if(stage==3)return Action.WAIT;
        if(now<start || now-start>=LIMIT_MS || !released){stage=3;return Action.ABORT;}
        if(stopped){stage=3;return Action.FINISH;}
        if(!settings)return Action.WAIT;
        if(stage==0 && targetPage && stopButton){stage=1;return Action.STOP;}
        if(stage==1 && confirmDialog){stage=2;return Action.CONFIRM;}
        return Action.WAIT;
    }
}
