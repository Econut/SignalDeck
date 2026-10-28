/* SignalDeck - GPL-3.0-or-later. Modified P8 CSV database bridge. */
package com.macdmr.radio;
import java.util.*;
import android.database.sqlite.SQLiteDatabase;

/** Uses the existing database; a validated import is a single atomic transaction. */
public final class ChannelCsvStore {
    private ChannelCsvStore(){}
    private static String key(String name){return name.trim().toLowerCase(Locale.ROOT);}
    public static List<ChannelCsv.Row> exportRows(ChannelStore store,List<ChannelStore.Channel> channels){
        List<ChannelCsv.Row> out=new ArrayList<>();List<ChannelStore.Zone> zones=store.zones();List<ChannelStore.ScanList> lists=store.scanLists();
        for(ChannelStore.Channel c:channels){ChannelCsv.Row row=new ChannelCsv.Row();row.channel=c;
            for(ChannelStore.Zone z:zones)for(int n:z.numbers)if(n==c.number){row.zones.add(z.name);break;}
            for(ChannelStore.ScanList s:lists)for(long id:s.channelIds)if(id==c.id){row.scanLists.add(s.name);break;}
            out.add(row);
        }return out;
    }
    private static Map<String,ChannelStore.Channel> existing(ChannelStore store){
        Map<String,ChannelStore.Channel> map=new HashMap<>();for(ChannelStore.Channel c:store.all()){
            String name=key(c.name);if(map.containsKey(name))map.put(name,null);else map.put(name,c);
        }return map;
    }
    public static int duplicates(ChannelStore store,ChannelCsv.Parsed parsed){
        if(!parsed.valid())throw new IllegalArgumentException("Fix the CSV errors before importing");Map<String,ChannelStore.Channel> map=existing(store);int count=0;
        for(ChannelCsv.Row row:parsed.rows){String name=key(row.channel.name);if(map.containsKey(name)){if(map.get(name)==null)throw new IllegalArgumentException("Line "+row.line+": more than one saved channel is named '"+row.channel.name+"'. Rename these channels before updating by name.");count++;}}
        return count;
    }
    /** Preview also permits replacement of an existing book with ambiguous duplicate names. */
    public static int matchingNames(ChannelStore store,ChannelCsv.Parsed parsed){Map<String,ChannelStore.Channel> map=existing(store);int count=0;for(ChannelCsv.Row r:parsed.rows)if(map.containsKey(key(r.channel.name)))count++;return count;}
    public static String apply(ChannelStore store,ChannelCsv.Parsed parsed,boolean update){
        return apply(store,parsed,update,false);
    }
    public static String apply(ChannelStore store,ChannelCsv.Parsed parsed,boolean update,boolean replace){
        if(!parsed.valid() || parsed.rows.isEmpty())throw new IllegalArgumentException("No valid CSV to import");
        if(replace)ChannelCsv.write(parsed.rows); // validate the complete replacement before any delete
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();int added=0,updated=0,skipped=0;
        try{
            if(replace){
                // Keep history, but detach old channel numbers so they cannot label new channels.
                db.execSQL("UPDATE history SET channel=0 WHERE channel>0");
                db.delete("nets","channelId IN (SELECT _id FROM channels)",null);
                db.delete("scanlists",null,null);db.delete("zones",null,null);db.delete("channels",null,null);
            }
            duplicates(store,parsed);Map<String,ChannelStore.Channel> old=existing(store);
            List<ChannelStore.Zone> zones=store.zones();List<ChannelStore.ScanList> lists=store.scanLists();
            Map<ChannelStore.Zone,LinkedHashSet<Integer>> zoneMembers=new LinkedHashMap<>();for(ChannelStore.Zone z:zones){LinkedHashSet<Integer> ids=new LinkedHashSet<>();for(int n:z.numbers)ids.add(n);zoneMembers.put(z,ids);}
            Map<ChannelStore.ScanList,LinkedHashSet<Long>> listMembers=new LinkedHashMap<>();for(ChannelStore.ScanList s:lists){LinkedHashSet<Long> ids=new LinkedHashSet<>();for(long id:s.channelIds)ids.add(id);listMembers.put(s,ids);}
            Set<ChannelStore.Zone> changedZones=new HashSet<>();Set<ChannelStore.ScanList> changedLists=new HashSet<>();
            for(ChannelCsv.Row row:parsed.rows){ChannelStore.Channel c=row.channel,was=old.get(key(c.name));
                if(was!=null && !update){skipped++;continue;}
                // Copy parsed fields, never mutate a reusable preview or its original channel objects.
                ChannelStore.Channel saved=copy(c);
                if(was!=null){if(was.encryptSw!=2)throw new IllegalArgumentException("Line "+row.line+": encrypted channels cannot be updated by CSV v1");saved.id=was.id;saved.number=was.number;saved.active=was.active;saved.location=was.location;saved.lat=was.lat;saved.lon=was.lon;saved.elev=was.elev;saved.m17dest=was.m17dest;store.update(saved);updated++;}
                else{if(store.insert(saved)<0)throw new IllegalStateException("Could not insert "+saved.name);added++;}
                Set<ChannelStore.Zone> chosenZones=new HashSet<>();for(String name:row.zones){ChannelStore.Zone z=findZone(zones,name);if(z==null){z=new ChannelStore.Zone();z.name=name;zones.add(z);zoneMembers.put(z,new LinkedHashSet<>());}chosenZones.add(z);}
                for(ChannelStore.Zone z:zones){Set<Integer> members=zoneMembers.get(z);boolean changed=chosenZones.contains(z)?members.add(saved.number):members.remove(saved.number);if(changed)changedZones.add(z);}
                Set<ChannelStore.ScanList> chosenLists=new HashSet<>();for(String name:row.scanLists){ChannelStore.ScanList s=findList(lists,name);if(s==null){s=new ChannelStore.ScanList();s.name=name;lists.add(s);listMembers.put(s,new LinkedHashSet<>());}chosenLists.add(s);}
                for(ChannelStore.ScanList s:lists){Set<Long> members=listMembers.get(s);boolean changed=chosenLists.contains(s)?members.add(saved.id):members.remove(saved.id);if(changed)changedLists.add(s);}
            }
            for(ChannelStore.Zone z:changedZones){z.numbers=new int[zoneMembers.get(z).size()];int i=0;for(int n:zoneMembers.get(z))z.numbers[i++]=n;store.saveZone(z);}
            for(ChannelStore.ScanList s:changedLists){s.channelIds=new long[listMembers.get(s).size()];int i=0;for(long id:listMembers.get(s))s.channelIds[i++]=id;store.saveScanList(s);}
            if(replace)store.setActive(1);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return replace?"Channel book replaced: "+added+" channels loaded":("Imported: "+added+" added, "+updated+" updated, "+skipped+" skipped");
    }
    private static ChannelStore.Zone findZone(List<ChannelStore.Zone> zones,String name){ChannelStore.Zone found=null;for(ChannelStore.Zone z:zones)if(z.name.equalsIgnoreCase(name)){if(found!=null)throw new IllegalArgumentException("Ambiguous saved zone name: "+name);found=z;}return found;}
    private static ChannelStore.ScanList findList(List<ChannelStore.ScanList> lists,String name){ChannelStore.ScanList found=null;for(ChannelStore.ScanList s:lists)if(s.name.equalsIgnoreCase(name)){if(found!=null)throw new IllegalArgumentException("Ambiguous saved scan-list name: "+name);found=s;}return found;}
    private static ChannelStore.Channel copy(ChannelStore.Channel c){
        ChannelStore.Channel n=new ChannelStore.Channel();n.name=c.name;n.rxFreq=c.rxFreq;n.txFreq=c.txFreq;n.type=c.type;n.band=c.band;n.rxType=c.rxType;n.rxSubCode=c.rxSubCode;n.txType=c.txType;n.txSubCode=c.txSubCode;n.sq=c.sq;n.power=c.power;n.rxOnly=c.rxOnly;n.cc=c.cc;n.inSlot=c.inSlot;n.outSlot=c.outSlot;n.txContact=c.txContact;n.contactType=c.contactType;n.groups=c.groups.clone();n.mode=c.mode;n.relay=c.relay;n.interrupt=c.interrupt;n.softSq=c.softSq;n.softSqLevel=c.softSqLevel;n.notes=c.notes;return n;
    }
}
