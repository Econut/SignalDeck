package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public class P8MainRadioTest {
    private ChannelStore.Channel fm(){ChannelStore.Channel c=new ChannelStore.Channel();c.name="Channel 9";c.type=1;c.rxFreq=446106250;c.txFreq=145000000;c.band=0;c.sq=1;return c;}
    @Test public void mainFmUsesSavedRxAndTxFrequencies(){Frame f=P8Protocol.decodeHex(Frame.hex(P8MainRadio.receiveChannel(fm(),false,4).packet(),28));assertEquals(0x23,f.cmd);ByteBuffer b=ByteBuffer.wrap(f.body).order(ByteOrder.LITTLE_ENDIAN);assertEquals(446106250,b.getInt());assertEquals(145000000,b.getInt());assertEquals(0,f.body[8]);assertEquals(0,f.body[9]);assertEquals(4,f.body[16]);assertEquals(2,f.body[17]);assertEquals(2,f.body[18]);}
    @Test public void vhfNarrowIsPreserved(){ChannelStore.Channel c=fm();c.rxFreq=145500000;assertEquals(0,P8MainRadio.receiveChannel(c,false,1).packet()[16]);}
    @Test public void toneAndReceiveOnlyAreMapped(){ChannelStore.Channel c=fm();c.rxType=1;c.rxSubCode=13;c.txType=3;c.txSubCode=82;c.rxOnly=true;P8RxProtocol.Channel wire=P8MainRadio.receiveChannel(c,false,1);byte[] b=wire.packet();assertEquals(1,b[19]);assertEquals(13,b[20]);assertEquals(3,b[21]);assertEquals(82,b[22]);assertFalse(wire.txAllowed);assertEquals(0,P8MainRadio.receiveChannel(c,true,1).packet()[19]);}
    @Test public void digitalStockLayout(){Frame f=P8DigitalProtocol.receive(438500000,888,new int[]{9,91},9,1,1,0,1,3);assertEquals(163,f.body.length);ByteBuffer b=ByteBuffer.wrap(f.body).order(ByteOrder.LITTLE_ENDIAN);assertEquals(438500000,b.getInt());assertEquals(438500000,b.getInt());assertEquals(888,b.getInt());assertEquals(9,b.getInt());assertEquals(91,b.getInt());assertEquals(9,b.getInt(140));assertEquals(1,f.body[144]);assertEquals(0,f.body[145]);assertEquals(1,f.body[146]);assertEquals(0,f.body[147]);assertEquals(1,f.body[148]);assertEquals(2,f.body[150]);assertEquals(2,f.body[159]);assertEquals(3,f.body[160]);assertEquals(1,f.body[161]);assertEquals(2,f.body[162]);}
    @Test public void digitalVolumeChangesOneFieldAndChecksum(){P8RxProtocol.Channel c=P8RxProtocol.Channel.digital("DMR",438500000,888,new int[]{9},9,1,1,0,0,1);byte[] packet=c.packet(9);assertEquals(172,packet.length);assertEquals(0x22,c.command());Frame f=P8Protocol.decodeHex(Frame.hex(packet,packet.length));assertEquals(9,f.body[160]);assertEquals(0,f.body[145]);}
    @Test(expected=IllegalArgumentException.class)public void rejectsOversizeGroups(){P8DigitalProtocol.receive(438500000,888,new int[33],9,1,1,0,0,1);}
    @Test(expected=IllegalArgumentException.class)public void rejectsInvalidSlot(){P8DigitalProtocol.receive(438500000,888,new int[]{9},9,1,1,2,0,1);}
}
