package com.macdmr.radio;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ChannelListScopeTest {
    private ChannelStore.Channel ch(int n){ChannelStore.Channel c=new ChannelStore.Channel();c.number=n;return c;}
    @Test public void allRetainsOrderWithoutSharingList(){List<ChannelStore.Channel> source=Arrays.asList(ch(1),ch(2));List<ChannelStore.Channel> result=ChannelListScope.inZone(source,null);assertEquals(source,result);assertNotSame(source,result);}
    @Test public void namedZoneFiltersWithoutChangingMembershipOrOrder(){ChannelStore.Zone z=new ChannelStore.Zone();z.numbers=new int[]{3,1,3,99};List<ChannelStore.Channel> source=Arrays.asList(ch(1),ch(2),ch(3));List<ChannelStore.Channel> result=ChannelListScope.inZone(source,z);assertEquals(2,result.size());assertEquals(1,result.get(0).number);assertEquals(3,result.get(1).number);assertArrayEquals(new int[]{3,1,3,99},z.numbers);assertEquals(3,source.size());}
    @Test public void emptyZoneShowsNothing(){assertTrue(ChannelListScope.inZone(Arrays.asList(ch(1)),new ChannelStore.Zone()).isEmpty());}
    @Test public void missingZoneFallsBackToAll(){ChannelStore.Zone z=new ChannelStore.Zone();z.id=5;assertEquals(0,ChannelListScope.resolve(6,Arrays.asList(z)));assertEquals(5,ChannelListScope.resolve(5,Arrays.asList(z)));assertEquals(-1,ChannelListScope.resolve(-99,Arrays.asList(z)));assertEquals(0,ChannelListScope.resolve(0,Arrays.asList(z)));}
}
