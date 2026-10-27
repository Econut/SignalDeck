/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive playback buffering. See COPYRIGHT. */
package com.macdmr.radio;
import java.util.ArrayDeque;
import java.util.Arrays;
/** Retains partial/nonblocking AudioTrack writes instead of silently discarding PCM. */
public final class P8AudioQueue {
    public interface Writer {int write(byte[] data,int offset,int count);}
    private final ArrayDeque<byte[]> chunks=new ArrayDeque<>();private int offset,pending;
    public int pending(){return pending;}
    public void add(byte[] bytes,int off,int n){if(n<0 || pending+n>32000)throw new IllegalStateException("Speaker backlog exceeded two seconds; stop RX");chunks.add(Arrays.copyOfRange(bytes,off,off+n));pending+=n;}
    public void drain(Writer writer){while(!chunks.isEmpty()){
        byte[] b=chunks.peek();int n=writer.write(b,offset,b.length-offset);
        if(n<0 || n>b.length-offset)throw new IllegalStateException("Speaker write failed: "+n);
        if(n==0)return;offset+=n;pending-=n;
        if(offset==b.length){chunks.remove();offset=0;}
    }}
}
