package com.macdmr.radio;
import java.util.*;
/** Browse-only scope. No radio service reference and no tuning/zone mutation. */
public final class ChannelListScope {
    public static final long FOLLOW=-1, ALL=0;
    private ChannelListScope(){}
    public static long resolve(long selected,List<ChannelStore.Zone> zones){
        if(selected<=0)return selected==ALL?ALL:FOLLOW;
        for(ChannelStore.Zone zone:zones)if(zone.id==selected)return selected;
        return ALL;
    }
    public static List<ChannelStore.Channel> inZone(List<ChannelStore.Channel> channels,ChannelStore.Zone zone){
        if(zone==null)return new ArrayList<>(channels);
        Set<Integer> members=new HashSet<>();for(int number:zone.numbers)members.add(number);
        List<ChannelStore.Channel> out=new ArrayList<>();for(ChannelStore.Channel ch:channels)if(members.contains(ch.number))out.add(ch);
        return out;
    }
}
