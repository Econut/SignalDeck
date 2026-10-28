/* SignalDeck - GPL-3.0-or-later. Scratch database only; no service or radio access. */
package com.macdmr.radio;
import android.content.Context;
import java.util.List;

final class P8ChannelManagementChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static ChannelStore.Channel channel(ChannelStore db,String name,int frequency){ChannelStore.Channel c=new ChannelStore.Channel();c.name=name;c.type=1;c.rxFreq=frequency;c.txFreq=frequency;c.rxOnly=true;db.insert(c);return c;}
    static void run(Context ctx){
        String file="p8-channel-management-scratch.db";ctx.deleteDatabase(file);ChannelStore db=new ChannelStore(ctx,file);
        try{
            ChannelStore.Channel a=channel(db,"One",446106250),b=channel(db,"Two",446118750),c=channel(db,"Three",446131250);
            ChannelStore.Zone local=new ChannelStore.Zone();local.name="Local";local.numbers=new int[]{a.number};db.saveZone(local);
            ChannelStore.Zone remote=new ChannelStore.Zone();remote.name="Other";remote.numbers=new int[]{c.number};db.saveZone(remote);
            ChannelStore.ScanList list=new ChannelStore.ScanList();list.name="Mixed";list.channelIds=new long[]{a.id,c.id,a.id,999999};db.saveScanList(list);
            check(list.channelIds.length==2,"Scan membership must deduplicate and reject missing IDs");
            check(db.scanChannels(list.id).size()==2,"List must cross zones");
            c.name="Edited";c.sq=7;c.band=0;c.power=1;c.rxType=1;c.rxSubCode=12;c.txType=3;c.txSubCode=23;c.softSq=true;c.softSqLevel=6;c.notes="Saved settings";db.update(c);
            long listId=list.id,zoneId=local.id,channelId=c.id;db.close();db=new ChannelStore(ctx,file);
            ChannelStore.Channel loaded=db.byNumber(c.number);
            check(loaded.id==channelId && loaded.name.equals("Edited") && loaded.rxFreq==446131250 && loaded.txFreq==446131250,"Channel identity/frequencies did not persist");
            check(loaded.rxOnly && loaded.sq==7 && loaded.band==0 && loaded.power==1 && loaded.rxType==1 && loaded.rxSubCode==12 && loaded.txType==3 && loaded.txSubCode==23 && loaded.softSq && loaded.softSqLevel==6 && loaded.notes.equals("Saved settings"),"Channel options did not persist");
            check(db.zone(zoneId).numbers[0]==a.number && db.scanList(listId).name.equals("Mixed"),"Zones/list not persistent");
            db.delete(b);List<ChannelStore.Channel> resolved=db.scanChannels(listId);
            check(resolved.size()==2 && resolved.get(1).id==channelId && resolved.get(1).number==2,"Renumber changed scan membership");
            db.delete(a);resolved=db.scanChannels(listId);check(resolved.size()==1 && resolved.get(0).id==channelId && resolved.get(0).number==1,"Deleted member selected wrong channel");
            db.deleteZone(remote.id);check(db.scanChannels(listId).size()==1,"Zone deletion changed independent scan list");
            list=db.scanList(listId);list.name="Renamed";list.channelIds=new long[]{channelId};db.saveScanList(list);
            check(db.scanLists().size()==1 && db.scanList(listId).name.equals("Renamed"),"List edit failed");
            ChannelStore.ScanList duplicate=new ChannelStore.ScanList();duplicate.name="renamed";boolean rejected=false;try{db.saveScanList(duplicate);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Duplicate name accepted");
            db.deleteScanList(listId);check(db.scanList(listId)==null && db.count()==1,"List deletion removed saved channels");
        }finally{db.close();ctx.deleteDatabase(file);}
    }
}
