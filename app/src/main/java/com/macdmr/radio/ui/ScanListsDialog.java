/* SignalDeck - GPL-3.0-or-later. Modified P8 scan-list management. */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.macdmr.radio.ChannelStore;
import com.macdmr.radio.RadioService;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Uses existing channels; a scan list never changes their zone assignments or radio settings. */
public final class ScanListsDialog {
    private ScanListsDialog(){}
    public static void show(Context c,RadioService svc){
        if(svc.scanning()){Toast.makeText(c,svc.toggleScan(),Toast.LENGTH_SHORT).show();return;}
        List<ChannelStore.ScanList> lists=svc.store().scanLists();
        String[] items=new String[lists.size()+2];
        items[0]="Current zone: "+svc.zoneName()+(svc.scanListId()==0?" ✓":"");
        for(int i=0;i<lists.size();i++){ChannelStore.ScanList s=lists.get(i);items[i+1]=s.name+" ("+svc.store().scanChannels(s.id).size()+")"+(svc.scanListId()==s.id?" ✓":"");}
        items[items.length-1]="Manage scan lists…";
        new AlertDialog.Builder(c).setTitle("Choose scan list").setItems(items,(d,w)->{
            if(w==items.length-1){manage(c,svc);return;}
            svc.setScanList(w==0?0:lists.get(w-1).id);
            Toast.makeText(c,svc.toggleScan(),Toast.LENGTH_LONG).show();
        }).setNegativeButton("Cancel",null).show();
    }
    public static void manage(Context c,RadioService svc){
        List<ChannelStore.ScanList> lists=svc.store().scanLists();String[] items=new String[lists.size()+1];
        for(int i=0;i<lists.size();i++)items[i]=lists.get(i).name+" ("+svc.store().scanChannels(lists.get(i).id).size()+")";
        items[lists.size()]="New scan list…";
        new AlertDialog.Builder(c).setTitle("Manage scan lists").setItems(items,(d,w)->{
            if(w==lists.size())edit(c,svc,new ChannelStore.ScanList());else options(c,svc,lists.get(w));
        }).setNegativeButton("Done",null).show();
    }
    private static void options(Context c,RadioService svc,ChannelStore.ScanList s){
        new AlertDialog.Builder(c).setTitle(s.name).setItems(new String[]{"Edit name and channels","Delete scan list"},(d,w)->{
            if(w==0)edit(c,svc,s);else new AlertDialog.Builder(c).setTitle("Delete "+s.name+"?").setMessage("Saved channels and zones are kept.")
                .setPositiveButton("Delete",(dd,ww)->{svc.store().deleteScanList(s.id);svc.channelCollectionsChanged();Toast.makeText(c,"Scan list deleted",Toast.LENGTH_SHORT).show();manage(c,svc);})
                .setNegativeButton("Cancel",null).show();
        }).show();
    }
    private static void edit(Context c,RadioService svc,ChannelStore.ScanList s){
        EditText name=new EditText(c);name.setSingleLine(true);name.setHint("Scan-list name");name.setText(s.name);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ChannelStore.NAME_MAX)});
        LinearLayout box=new LinearLayout(c);box.setPadding(24,8,24,0);box.addView(name,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(s.id==0?"New scan list":"Edit scan list").setView(box)
            .setPositiveButton("Choose channels",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Enter a name");return;}
            for(ChannelStore.ScanList other:svc.store().scanLists())if(other.id!=s.id && other.name.equalsIgnoreCase(n)){name.setError("Name already used");return;}
            s.name=n;dialog.dismiss();pick(c,svc,s);
        }));dialog.show();
    }
    private static void pick(Context c,RadioService svc,ChannelStore.ScanList s){
        List<ChannelStore.Channel> all=svc.store().all();String[] labels=new String[all.size()];boolean[] selected=new boolean[all.size()];
        Set<Long> ids=new HashSet<>();for(long id:s.channelIds)ids.add(id);
        List<ChannelStore.Zone> zones=svc.store().zones();
        for(int i=0;i<all.size();i++){
            ChannelStore.Channel ch=all.get(i);StringBuilder names=new StringBuilder();
            for(ChannelStore.Zone z:zones)for(int n:z.numbers)if(n==ch.number){if(names.length()>0)names.append(", ");names.append(z.name);break;}
            labels[i]=String.format(Locale.US,"%02d %s · %.5f MHz",ch.number,ch.name,ch.rxFreq/1e6)+(names.length()==0?"":"\n"+names);
            selected[i]=ids.contains(ch.id);
        }
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(s.name+" — all zones").setMultiChoiceItems(labels,selected,(d,w,on)->selected[w]=on)
            .setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int count=0;for(boolean on:selected)if(on)count++;s.channelIds=new long[count];int k=0;for(int i=0;i<all.size();i++)if(selected[i])s.channelIds[k++]=all.get(i).id;
            try{svc.store().saveScanList(s);svc.channelCollectionsChanged();Toast.makeText(c,"Saved “"+s.name+"” · "+count+" channels",Toast.LENGTH_LONG).show();dialog.dismiss();}
            catch(IllegalArgumentException e){Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
}
