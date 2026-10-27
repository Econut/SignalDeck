/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 TX test guard. */
package com.macdmr.radio;
/** One explicitly armed, short first-test interval. Never persisted or armed by a broadcast. */
public final class P8TxPolicy {
    public static final int TEST_HZ=446106250;
    public static final long MAX_KEY_MS=2000,ARM_LIFETIME_MS=60000;
    private long armedUntil;private boolean armed;
    public synchronized void arm(long now){armed=true;armedUntil=now+ARM_LIFETIME_MS;}
    public synchronized boolean consume(long now,int hz,int command){boolean ok=armed && now<armedUntil && hz==TEST_HZ && command==0x23;armed=false;return ok;}
    public synchronized void cancel(){armed=false;}
}
