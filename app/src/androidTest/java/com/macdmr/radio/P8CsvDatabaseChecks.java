/* SignalDeck - GPL-3.0-or-later. Scratch CSV/database integration; no radio access. */
package com.macdmr.radio;
import android.content.Context;
import java.util.*;

final class P8CsvDatabaseChecks {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static ChannelStore.Channel add(ChannelStore store,String name){ChannelStore.Channel c=new ChannelStore.Channel();c.name=name;c.type=1;c.rxFreq=c.txFreq=446106250;c.band=0;c.rxOnly=true;store.insert(c);return c;}
    static void run(Context context){
        String filename="p8-csv-scratch.db";context.deleteDatabase(filename);ChannelStore store=new ChannelStore(context,filename);
        try{
            ChannelStore.Channel original=add(store,"Original"),other=add(store,"Unrelated");original.active=true;original.lat=51.25;original.location="Keep location";store.update(original);
            ChannelStore.Zone zone=new ChannelStore.Zone();zone.name="Existing";zone.numbers=new int[]{original.number,other.number};store.saveZone(zone);
            ChannelStore.ScanList list=new ChannelStore.ScanList();list.name="Existing scan";list.channelIds=new long[]{original.id,other.id};store.saveScanList(list);
            ChannelCsv.Parsed input=ChannelCsv.parse(ChannelCsv.template(true));input.rows.get(0).channel.name="original";input.rows.get(0).channel.rxFreq=446106251;
            check(ChannelCsvStore.duplicates(store,input)==1,"Duplicate preview did not match ignoring case");
            String skipped=ChannelCsvStore.apply(store,input,false);check(skipped.contains("1 added") && skipped.contains("1 skipped"),"Skip report wrong");
            check(store.byNumber(original.number).rxFreq==446106250,"Skip changed original");check(store.scanChannels(list.id).size()==2,"Skip changed original membership");
            String updated=ChannelCsvStore.apply(store,input,true);check(updated.contains("2 updated"),"Repeat import created duplicates");
            long stable=original.id;store.close();store=new ChannelStore(context,filename);
            ChannelStore.Channel saved=store.byNumber(original.number);check(saved.id==stable && saved.rxFreq==446106251 && saved.active && saved.lat==51.25 && saved.location.equals("Keep location"),"Update lost identity, precision or unrelated metadata");
            check(store.count()==3 && store.zones().size()==3 && store.scanLists().size()==2,"Auto-created zones/lists or restart persistence failed");
            check(store.zone(zone.id).numbers.length==1 && store.zone(zone.id).numbers[0]==other.number,"Update removed unrelated zone members");
            check(store.scanChannels(list.id).size()==1 && store.scanChannels(list.id).get(0).id==other.id,"Update removed unrelated scan members");
            ChannelStore.ScanList mixed=null;for(ChannelStore.ScanList s:store.scanLists())if(s.name.equals("My scan"))mixed=s;
            check(mixed!=null && store.scanChannels(mixed.id).size()==2,"Cross-zone imported list not reconstructed");
            String csv=ChannelCsv.write(ChannelCsvStore.exportRows(store,store.scanChannels(mixed.id)));check(ChannelCsv.parse(csv).rows.size()==2,"Scan-list export did not use standard format");
            ChannelCsv.Parsed skipOnly=ChannelCsv.parse(csv);skipOnly.rows.get(0).zones=Arrays.asList("Do not create");ChannelCsvStore.apply(store,skipOnly,false);for(ChannelStore.Zone z:store.zones())check(!z.name.equals("Do not create"),"Skipped row created a zone");
            // A later ambiguity must roll back earlier inserts in the same import.
            ChannelStore.Zone clash=new ChannelStore.Zone();clash.name="Clash";store.saveZone(clash);ChannelStore.Zone clash2=new ChannelStore.Zone();clash2.name="clash";store.saveZone(clash2);
            ChannelCsv.Parsed bad=ChannelCsv.parse(ChannelCsv.template(true));bad.rows.get(0).channel.name="First new";bad.rows.get(1).channel.name="Second new";bad.rows.get(1).zones=Arrays.asList("CLASH");int before=store.count();boolean rejected=false;
            try{ChannelCsvStore.apply(store,bad,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected && store.count()==before,"Import failure was not atomic");
            ChannelCsv.Parsed invalid=ChannelCsv.parse(csv.replace("446.106251","oops"));check(!invalid.valid(),"Invalid frequency accepted");rejected=false;try{ChannelCsvStore.apply(store,invalid,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected && store.count()==before,"Invalid rows changed database");
            store.addHistory(original.number,"test","Keep historical text",-90,0);
            store.getWritableDatabase().execSQL("INSERT INTO nets(channelId,name) VALUES(?,?)",new Object[]{original.id,"Old channel schedule"});
            store.getWritableDatabase().execSQL("INSERT INTO contacts(name,number,type) VALUES('Keep contact',1234,1)");
            String oldBook=ChannelCsv.write(ChannelCsvStore.exportRows(store,store.all()));
            store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER csv_replace_failure BEFORE INSERT ON channels WHEN NEW.name='Example DMR' BEGIN SELECT RAISE(ABORT,'intentional replacement test failure'); END");
            ChannelCsv.Parsed replacement=ChannelCsv.parse(ChannelCsv.template(true));rejected=false;
            try{ChannelCsvStore.apply(store,replacement,true,true);}catch(RuntimeException expected){rejected=true;}
            check(rejected && oldBook.equals(ChannelCsv.write(ChannelCsvStore.exportRows(store,store.all()))),"Failed replacement did not restore original book");
            check(android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(),"SELECT COUNT(*) FROM nets",null)==1,"Rollback lost channel schedule");
            store.getWritableDatabase().execSQL("DROP TRIGGER csv_replace_failure");
            check(ChannelCsvStore.apply(store,replacement,true,true).contains("2 channels loaded"),"Replacement report incorrect");
            check(store.count()==2 && store.byNumber(1).name.startsWith("Example FM") && store.byNumber(2).name.equals("Example DMR"),"Replacement retained old channels or wrong order");
            check(store.byNumber(1).id!=original.id && store.activeNumber()==1,"Replacement reused identity or wrong active channel");
            check(store.zones().size()==2 && store.scanLists().size()==1 && store.scanChannels(store.scanLists().get(0).id).size()==2,"Replacement retained old groups or lost new members");
            check(store.contacts().size()==1 && android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(),"SELECT COUNT(*) FROM history WHERE channel=0",null)==1,"Replacement lost contacts/history");
            check(android.database.DatabaseUtils.longForQuery(store.getReadableDatabase(),"SELECT COUNT(*) FROM nets",null)==0,"Replacement retained orphaned channel schedule");
            store.close();store=new ChannelStore(context,filename);check(store.count()==2 && store.zones().size()==2 && store.scanLists().size()==1,"Replacement did not persist");
            rejected=false;try{ChannelCsvStore.apply(store,ChannelCsv.parse(ChannelCsv.template(false)),true,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected && store.count()==2,"Empty replacement cleared channels");
        }finally{store.close();context.deleteDatabase(filename);}
    }
}
