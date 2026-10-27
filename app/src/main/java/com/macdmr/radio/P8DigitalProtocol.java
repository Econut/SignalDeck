/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 adapter. */
package com.macdmr.radio;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/** Stock w2.b.Q / y2.d: direct mode, encryption off, receive configuration only. */
public final class P8DigitalProtocol {
    private P8DigitalProtocol(){}
    public static Frame receive(int hz,int localId,int[] groups,int target,int callType,int cc,int inSlot,int outSlot,int volume){
        new P8RxProtocol.Channel("DMR",hz,0,1,2,volume);
        if(localId<1 || localId>0xffffff || target<1 || target>0xffffff || callType<0 || callType>2 || cc<0 || cc>15 || inSlot<0 || inSlot>1 || outSlot<0 || outSlot>1 || groups==null || groups.length>32)throw new IllegalArgumentException("Invalid DMR IDs, groups, colour code or slots");
        ByteBuffer b=ByteBuffer.allocate(163).order(ByteOrder.LITTLE_ENDIAN);b.putInt(hz).putInt(hz).putInt(localId);
        for(int i=0;i<32;i++){int g=i<groups.length?groups[i]:0;if(g<0 || g>0xffffff)throw new IllegalArgumentException("Invalid DMR group ID");b.putInt(g);}
        b.putInt(callType==2?0xffffff:target).put((byte)callType).put((byte)0).put((byte)cc).put((byte)inSlot).put((byte)outSlot)
            .put((byte)0).put((byte)2).put(new byte[8]).put((byte)2).put((byte)volume).put((byte)1).put((byte)2);
        return Frame.request(0x22,b.array());
    }
}
