/* SignalDeck - GPL-3.0-or-later. Modified P8 normal analogue operation. */
package com.macdmr.radio;
/** A timeout or refused key never rekeys until the physical/on-screen press is released. */
public final class P8PttEdge {
    private boolean held;
    public synchronized boolean press(){if(held)return false;held=true;return true;}
    public synchronized void release(){held=false;}
}
