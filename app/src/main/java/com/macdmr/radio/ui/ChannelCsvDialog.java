/* SignalDeck - GPL-3.0-or-later. Modified P8 CSV file workflow. */
package com.macdmr.radio.ui;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.macdmr.radio.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

public final class ChannelCsvDialog {
    private ChannelCsvDialog(){}
    private static MainActivity activity(Context c){return (MainActivity)c;}
    private static void error(Context c,String message){
        TextView text=new TextView(c);text.setText(message);text.setTextIsSelectable(true);text.setPadding(24,16,24,16);ScrollView scroll=new ScrollView(c);scroll.addView(text);
        new AlertDialog.Builder(c).setTitle("CSV not imported / exported").setView(scroll).setPositiveButton("OK",null).show();
    }
    private static ProgressDialog busy(Context c,String message){ProgressDialog p=new ProgressDialog(c);p.setMessage(message);p.setCancelable(false);p.show();return p;}
    public static String readUtf8(InputStream in) throws Exception{
        if(in==null)throw new IOException("Cannot open the selected file");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
        while((n=in.read(chunk))!=-1){if(bytes.size()+n>ChannelCsv.MAX_CHARS)throw new IOException("CSV exceeds 2 MB");bytes.write(chunk,0,n);}
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
    public static void importFile(Context c,RadioService svc,Runnable reload){
        activity(c).pickDocument(uri->{ProgressDialog progress=busy(c,"Checking CSV…");
            new Thread(()->{try{
                ChannelCsv.Parsed parsed;try(InputStream in=c.getContentResolver().openInputStream(uri)){parsed=ChannelCsv.parse(readUtf8(in));}
                if(!parsed.valid())throw new IllegalArgumentException("Nothing changed. Fix these rows and import again:\n\n"+String.join("\n",parsed.errors));
                int duplicates=ChannelCsvStore.duplicates(svc.store(),parsed);
                activity(c).runOnUiThread(()->{progress.dismiss();preview(c,svc,parsed,duplicates,reload);});
            }catch(Exception e){activity(c).runOnUiThread(()->{progress.dismiss();error(c,e instanceof CharacterCodingException?"Use UTF-8 CSV (Excel: CSV UTF-8). The file contains invalid UTF-8.":e.getMessage());});}},"channel-csv-check").start();
        });
    }
    private static void preview(Context c,RadioService svc,ChannelCsv.Parsed parsed,int duplicates,Runnable reload){
        String message=parsed.rows.size()+" valid rows\n"+(parsed.rows.size()-duplicates)+" new channels, "+duplicates+" matching saved names.\n\nDuplicate matching uses channel name, ignoring case. Update replaces the listed settings and zone/scan-list memberships for those channels; other channels are kept. Skip leaves matching channels completely unchanged. Missing named zones and scan lists are created.\n\nNo rows are saved until you choose Import/Update or Skip.";
        AlertDialog.Builder b=new AlertDialog.Builder(c).setTitle("Import CSV").setMessage(message).setNegativeButton("Cancel",null);
        b.setPositiveButton(duplicates==0?"Import":"Update matches",(d,w)->apply(c,svc,parsed,true,reload));
        if(duplicates>0)b.setNeutralButton("Skip matches",(d,w)->apply(c,svc,parsed,false,reload));b.show();
    }
    private static void apply(Context c,RadioService svc,ChannelCsv.Parsed parsed,boolean update,Runnable reload){
        String blocked=svc.beginChannelCsvImport();if(blocked!=null){Toast.makeText(c,blocked,Toast.LENGTH_LONG).show();return;}
        ProgressDialog progress=busy(c,"Saving channels…");
        new Thread(()->{boolean saved=false;String message;
            try{message=ChannelCsvStore.apply(svc.store(),parsed,update);saved=true;}catch(Exception e){message="Nothing imported: "+e.getMessage();}
            final boolean success=saved;final String report=message;activity(c).runOnUiThread(()->{svc.endChannelCsvImport(success);progress.dismiss();reload.run();if(success)new AlertDialog.Builder(c).setTitle("CSV saved").setMessage(report).setPositiveButton("OK",null).show();else error(c,report);});
        },"channel-csv-save").start();
    }
    public static void exportFile(Context c,RadioService svc){
        new AlertDialog.Builder(c).setTitle("Export CSV").setItems(new String[]{"All channels","A selected zone","A saved scan list","Blank template","Template with example rows"},(d,w)->{
            if(w==0)export(c,svc,svc.store().all(),"P8-channels.csv");
            else if(w==1){List<ChannelStore.Zone> zones=svc.store().zones();if(zones.isEmpty()){Toast.makeText(c,"No named zones yet",Toast.LENGTH_SHORT).show();return;}String[] names=new String[zones.size()];for(int i=0;i<names.length;i++)names[i]=zones.get(i).name;
                new AlertDialog.Builder(c).setTitle("Export zone").setItems(names,(dd,i)->{Set<Integer> members=new HashSet<>();for(int n:zones.get(i).numbers)members.add(n);List<ChannelStore.Channel> selected=new ArrayList<>();for(ChannelStore.Channel ch:svc.store().all())if(members.contains(ch.number))selected.add(ch);export(c,svc,selected,"P8-zone.csv");}).show();}
            else if(w==2){List<ChannelStore.ScanList> lists=svc.store().scanLists();if(lists.isEmpty()){Toast.makeText(c,"No scan lists yet",Toast.LENGTH_SHORT).show();return;}String[] names=new String[lists.size()];for(int i=0;i<names.length;i++)names[i]=lists.get(i).name;
                new AlertDialog.Builder(c).setTitle("Export scan list").setItems(names,(dd,i)->export(c,svc,svc.store().scanChannels(lists.get(i).id),"P8-scan-list.csv")).show();}
            else save(c,w==3?"P8-channel-template.csv":"P8-channel-examples.csv",ChannelCsv.template(w==4));
        }).show();
    }
    private static void export(Context c,RadioService svc,List<ChannelStore.Channel> channels,String filename){
        if(channels.isEmpty()){Toast.makeText(c,"No channels in this selection",Toast.LENGTH_LONG).show();return;}
        ProgressDialog progress=busy(c,"Preparing CSV…");new Thread(()->{try{String text=ChannelCsv.write(ChannelCsvStore.exportRows(svc.store(),channels));activity(c).runOnUiThread(()->{progress.dismiss();save(c,filename,text);});}
            catch(Exception e){activity(c).runOnUiThread(()->{progress.dismiss();error(c,e.getMessage());});}},"channel-csv-export").start();
    }
    private static void save(Context c,String filename,String text){
        activity(c).createCsvDocument(filename,uri->{ProgressDialog progress=busy(c,"Writing CSV…");new Thread(()->{try{
            try(OutputStream out=c.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot open destination");out.write(text.getBytes(StandardCharsets.UTF_8));}
            activity(c).runOnUiThread(()->{progress.dismiss();Toast.makeText(c,"CSV saved",Toast.LENGTH_LONG).show();});
        }catch(Exception e){activity(c).runOnUiThread(()->{progress.dismiss();error(c,"Could not save CSV: "+e.getMessage());});}},"channel-csv-write").start();});
    }
}
