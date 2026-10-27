/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive-only screen. See COPYRIGHT. */
package com.macdmr.radio;
import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.*;
import org.json.*;
import java.util.*;
import android.media.AudioManager;
import android.content.SharedPreferences;
import android.os.Handler;

/** Receive-only foreground screen. Manual Android force-stop handover; never executes a shell.
 * Shell may supply a bounded auto-test after externally confirming release. No boot takeover.
 */
public final class P8ReceiveActivity extends Activity {
    private final ArrayList<P8RxProtocol.Channel> book=new ArrayList<>();
    private final ArrayList<String> events=new ArrayList<>();
    private final ArrayList<Integer> moduleAcks=new ArrayList<>();private final ArrayList<JSONObject> measurements=new ArrayList<>();
    private Spinner select;private EditText name,frequency,squelch;private CheckBox wide,monitor;
    private TextView status,notice,moduleLabel,mediaLabel,gainLabel,meterText;private ProgressBar meterBar;
    private SeekBar moduleBar,mediaBar,gainBar;private AudioManager audioManager;private SharedPreferences preferences;
    private int moduleLevel=1,savedMedia,lastMedia;private boolean trackingMedia;private final Handler handler=new Handler();
    private P8Receiver receiver;private boolean active,closing,scanning,foreground;
    private int volumeTestStage;private boolean volumeSequence;
    private int gain=1;private String testSession;private boolean autoPending;private int autoDuration;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(20,20,20,20);
        TextView title=new TextView(this);title.setText("UNIWA P8 — RECEIVE ONLY\nNo PTT / TX controls. Keep radio buttons untouched.\nUse App info → Force stop Intercom, return here, then Connect. Leaving this screen closes RX and restores Intercom.");body.addView(title);
        status=new TextView(this);status.setTextIsSelectable(true);body.addView(status);
        notice=new TextView(this);notice.setTextIsSelectable(true);body.addView(notice);
        if(!BuildConfig.P8_OFFLINE || !BuildConfig.P8_OPEN_PROBE_REVIEWED){status.setText("Reviewed P8 lab build required");setContentView(body);return;}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences=getSharedPreferences("p8_rx",0);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        moduleLevel=Math.max(1,Math.min(9,preferences.getInt("moduleVolume",1)));gain=Math.max(1,Math.min(4,preferences.getInt("softwareGain",1)));
        lastMedia=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);savedMedia=Math.max(0,Math.min(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),preferences.getInt("mediaVolume",lastMedia)));
        moduleLabel=new TextView(this);body.addView(moduleLabel);moduleBar=new SeekBar(this);moduleBar.setMax(8);moduleBar.setProgress(moduleLevel-1);body.addView(moduleBar);
        moduleLabel.setText("Module receive level: "+moduleLevel+" (saved; applies on Connect; development range 1–9)");
        TextView moduleHelp=new TextView(this);moduleHelp.setText("Module command is acknowledged, but higher levels did not increase UART PCM noise in testing. Default 1; use software gain/media volume for playback.");body.addView(moduleHelp);
        moduleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)moduleLabel.setText("Module receive level: "+(p+1)+" — release to save/apply");}public void onStopTrackingTouch(SeekBar b){moduleLevel=b.getProgress()+1;if(saveSetting("moduleVolume",moduleLevel)){moduleLabel.setText("Saved module level "+moduleLevel+(active?" — awaiting ACK":" — applies on Connect"));if(active)receiver.moduleVolume(moduleLevel);}}});
        mediaLabel=new TextView(this);body.addView(mediaLabel);mediaBar=new SeekBar(this);mediaBar.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));mediaBar.setProgress(lastMedia);body.addView(mediaBar);refreshMedia();
        mediaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){trackingMedia=true;}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)mediaLabel.setText("Android media: "+p+"/"+b.getMax()+" — release to save");}public void onStopTrackingTouch(SeekBar b){try{audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,b.getProgress(),0);savedMedia=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);saveSetting("mediaVolume",savedMedia);lastMedia=savedMedia;}catch(Exception e){show("Media volume not applied: "+e.getMessage());}trackingMedia=false;refreshMedia();}});
        gainLabel=new TextView(this);body.addView(gainLabel);gainBar=new SeekBar(this);gainBar.setMax(P8AudioSettings.INITIAL_GAIN_MAX-1);gainBar.setProgress(gain-1);body.addView(gainBar);gainLabel.setText("Saved software gain ×"+gain+" (maximum ×4)");
        gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)gainLabel.setText("Software gain ×"+(p+1)+" — release to save");}public void onStopTrackingTouch(SeekBar b){gain=b.getProgress()+1;if(saveSetting("softwareGain",gain)){gainLabel.setText("Saved software gain ×"+gain+" (maximum ×4)");if(active)receiver.gain(gain);}}});
        meterText=new TextView(this);meterText.setText("PCM input level: idle · clipping is counted per session");body.addView(meterText);meterBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meterBar.setMax(96);body.addView(meterBar);
        loadBook();select=new Spinner(this);body.addView(select);refreshList();
        name=field(body,"Channel name","P8 RX");frequency=field(body,"RX MHz","446.00625");squelch=field(body,"Squelch 0–9","1");
        wide=new CheckBox(this);wide.setText("Wide bandwidth (off = narrow)");body.addView(wide);
        monitor=new CheckBox(this);monitor.setText("Monitor open (unsquelched; pauses scanning on noise)");body.addView(monitor);
        select.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){fill(book.get(pos));}});
        button(body,"Save / update selected channel",()->{if(active){show("Disconnect before editing saved channels");return;}try{P8RxProtocol.Channel ch=edited();int i=select.getSelectedItemPosition();if(i>=0 && i<book.size())book.set(i,ch);else book.add(ch);saveBook();refreshList();select.setSelection(Math.max(0,i));}catch(Exception e){show(e.toString());}});
        button(body,"Add as new channel",()->{if(active){show("Disconnect before editing saved channels");return;}try{if(book.size()>=64)throw new Exception("Maximum 64 RX channels");book.add(edited());saveBook();refreshList();select.setSelection(book.size()-1);}catch(Exception e){show(e.toString());}});
        button(body,"Delete selected channel",()->{if(active){show("Disconnect before deleting channels");return;}if(book.size()>1){book.remove(select.getSelectedItemPosition());saveBook();refreshList();}});
        button(body,"Import supported FM channels from SignalDeck",()->{if(active){show("Disconnect before importing");return;}int count=0;try(ChannelStore store=new ChannelStore(this)){
            for(ChannelStore.Channel ch:store.all())if(ch.type==ChannelStore.Channel.TYPE_FM && ch.rxType==0 && ch.txType==0 && book.size()<64){
                try{P8RxProtocol.Channel item=new P8RxProtocol.Channel(ch.name,ch.rxFreq,ch.band,Math.max(0,Math.min(9,ch.sq)),2,1);boolean exists=false;for(P8RxProtocol.Channel b:book)if(b.hz==item.hz && b.name.equals(item.name))exists=true;if(!exists){book.add(item);count++;}}catch(IllegalArgumentException ignored){}
            }}catch(Exception e){show(e.toString());return;}if(!saveBook())return;refreshList();show("Saved: imported "+count+" plain FM channels; stock DB untouched. Tones/DMR omitted.");});
        button(body,"Intercom App info (manual Force stop)",()->{if(active){show("Close RX first");return;}startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:com.wtwd.ucee.dmr")));});
        button(body,"Connect RX / tune edited channel",()->connect());
        button(body,"Tune selected saved channel",()->{if(active){scanning=false;receiver.tune(book.get(select.getSelectedItemPosition()));}else show("Connect first");});
        button(body,"Start / stop scan (saved channels)",()->{if(!active){show("Connect first");return;}if(book.size()<2){show("Save at least two channels");return;}scanning=!scanning;receiver.scan(scanning);});
        button(body,"Stop RX and restore Intercom",()->disconnect());
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        String snapshot=getIntent().getStringExtra("settings_snapshot");
        if(snapshot!=null && snapshot.matches("[a-zA-Z0-9_-]{8,64}"))status.post(()->{try{JSONObject r=settingsSnapshot();try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"settings-"+snapshot+".json"))){f.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}finishAndRemoveTask();}catch(Exception e){show("Snapshot failed: "+e);}});
        if(state==null && getIntent().getBooleanExtra("auto_test",false)) {
            testSession=getIntent().getStringExtra("session");
            if(testSession!=null && testSession.matches("[a-zA-Z0-9_-]{8,64}")){
                volumeSequence=getIntent().getBooleanExtra("volume_sequence",false);
                if(getIntent().getBooleanExtra("auto_scan",false)){book.clear();book.add(new P8RxProtocol.Channel("Scan A",446006250,0,1,2,1));book.add(new P8RxProtocol.Channel("Scan B",446018750,0,1,2,1));refreshList();}
                autoDuration=Math.max(2000,Math.min(15000,getIntent().getIntExtra("duration",5000)));autoPending=true;
                status.postDelayed(()->{if(autoPending){autoPending=false;writeReceipt(true,"No UART opened: foreground/secure lock prevented test launch");restoreStock();}},10000);
            }
        }
    }
    private EditText field(LinearLayout p,String label,String value){TextView title=new TextView(this);title.setText(label);p.addView(title);EditText f=new EditText(this);f.setHint(label);f.setText(value);p.addView(f);return f;}
    private void button(LinearLayout p,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());p.addView(b);}
    private void show(String text){notice.setText(text);}
    private boolean saveSetting(String key,int value){String label=key.equals("moduleVolume")?"module level":key.equals("softwareGain")?"software gain":"Android media volume";boolean ok=preferences.edit().putInt(key,value).commit();show(ok?"Saved "+label+" = "+value:"SAVE FAILED: "+label);return ok;}
    private void refreshMedia(){int current=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);mediaLabel.setText("Android media: "+current+"/"+mediaBar.getMax()+" · saved "+savedMedia);if(!trackingMedia)mediaBar.setProgress(current);}
    private final Runnable mediaPoll=new Runnable(){public void run(){if(audioManager!=null && foreground){int n=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);if(!trackingMedia && n!=lastMedia){lastMedia=n;savedMedia=n;saveSetting("mediaVolume",n);}if(!trackingMedia)refreshMedia();handler.postDelayed(this,500);}}};
    private P8RxProtocol.Channel edited(){double mhz=Double.parseDouble(frequency.getText().toString().trim());if(!Double.isFinite(mhz))throw new IllegalArgumentException("Invalid frequency");return new P8RxProtocol.Channel(name.getText().toString(),Math.toIntExact(Math.round(mhz*1000000)),wide.isChecked()?1:0,Integer.parseInt(squelch.getText().toString().trim()),monitor.isChecked()?1:2,moduleLevel);}
    private void fill(P8RxProtocol.Channel ch){if(name==null)return;name.setText(ch.name);frequency.setText(String.format(java.util.Locale.US,"%.5f",ch.hz/1e6));squelch.setText(""+ch.squelch);wide.setChecked(ch.bandwidth==1);monitor.setChecked(ch.monitor==1);}
    private void refreshList(){select.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,book));}
    private void connect(){
        if(!foreground){show("RX requires this screen to be foreground and unlocked");writeReceipt(true,"No UART opened: screen not foreground");return;}
        if(active){try{scanning=false;receiver.tune(edited());}catch(Exception e){show(e.toString());}return;}
        try{
            if((getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&android.content.pm.ApplicationInfo.FLAG_STOPPED)==0)throw new Exception("Force stop Intercom in Android App info first; swiping away is not sufficient");
            P8RxProtocol.Channel target=edited();P8RxProtocol.Channel[] list=book.toArray(new P8RxProtocol.Channel[0]);
            receiver=new P8Receiver(this,new P8Receiver.Listener(){public void status(String text){runOnUiThread(()->{if(events.size()>=32)events.remove(0);events.add(text);status.setText(text);});}public void closed(boolean safe,String reason){runOnUiThread(()->closedReceiver(safe,reason));}
                public void moduleApplied(int level){runOnUiThread(()->{moduleAcks.add(level);events.add("Module receive level ACK: "+level);moduleLabel.setText("Saved module level "+moduleLevel+" · applied ACK "+level);
                    if(volumeSequence && volumeTestStage<3){int[] next={2,9,1};int target=next[volumeTestStage++];handler.postDelayed(()->{if(active){moduleLevel=target;receiver.moduleVolume(target);}},2200);}});}
                public void levels(double peak,double rms,long clips,long recent){runOnUiThread(()->{meterBar.setProgress((int)Math.max(0,Math.min(96,96+peak)));meterText.setText(String.format(java.util.Locale.US,"PCM input peak %.1f dBFS · RMS %.1f dBFS\nClipped samples: %d total · %d recent",peak,rms,clips,recent));meterText.setTextColor(recent>0?android.graphics.Color.RED:android.graphics.Color.rgb(60,180,90));
                    try{if(measurements.size()>=64)measurements.remove(0);measurements.add(new JSONObject().put("module",moduleLevel).put("peakDb",peak).put("rmsDb",rms).put("clips",clips));}catch(Exception ignored){}
                    if(events.size()<30)events.add(String.format(java.util.Locale.US,"LEVEL module=%d peak=%.1f rms=%.1f clips=%d",moduleLevel,peak,rms,clips));});}});
            if(testSession==null)audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,savedMedia,0);lastMedia=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);refreshMedia();
            active=true;closing=false;receiver.gain(gain);receiver.muteTestPlayback(volumeSequence);receiver.moduleVolume(moduleLevel);receiver.start(list,Math.max(0,select.getSelectedItemPosition()),target);show("Opening root-free RX session…");
            if(testSession!=null && getIntent().getBooleanExtra("auto_scan",false)){scanning=true;receiver.scan(true);}
        }catch(Exception e){active=false;show(e.toString());writeReceipt(false,e.toString());}
    }
    private void disconnect(){if(receiver!=null && active){closing=true;receiver.stop();}else if(testSession==null){restoreStock();}}
    private void closedReceiver(boolean safe,String reason){active=false;scanning=false;show(reason);writeReceipt(safe,reason);if(safe)restoreStock();else show(reason+"\nNo automatic handback: report this error.");}
    private void restoreStock(){try{startActivity(new Intent().setComponent(new ComponentName("com.wtwd.ucee.dmr","com.wtwd.ucee.dmr.common.MainActivity")));}catch(Exception e){show("RX closed; could not relaunch Intercom: "+e);}}
    private void writeReceipt(boolean safe,String text){if(testSession==null)return;try{
        JSONObject r=new JSONObject().put("safeClosed",safe).put("result",text).put("uid",android.system.Os.getuid()).put("ptt",P8Receiver.readState(P8Protocol.PTT_NODE)).put("events",new JSONArray(events)).put("moduleAcks",new JSONArray(moduleAcks)).put("measurements",new JSONArray(measurements)).put("settings",settingsSnapshot());
        try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"rx-"+testSession+".json"))){f.write(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }catch(Exception ignored){}}
    private JSONObject settingsSnapshot() throws Exception{return new JSONObject().put("storedModule",preferences.getInt("moduleVolume",1)).put("storedGain",preferences.getInt("softwareGain",1)).put("storedMedia",preferences.getInt("mediaVolume",audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))).put("actualMedia",audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).put("gainLimit",P8AudioSettings.INITIAL_GAIN_MAX).put("notice",notice.getText().toString());}
    @Override protected void onStop(){super.onStop();if(active && !closing){closing=true;receiver.stop();}}
    private void beginAutoIfReady(){if(!autoPending || !foreground || !hasWindowFocus())return;autoPending=false;
        if(volumeSequence){moduleLevel=1;gain=1;}
        frequency.setText(getIntent().getStringExtra("frequency")==null?"446.00625":getIntent().getStringExtra("frequency"));monitor.setChecked(getIntent().getBooleanExtra("monitor",false));connect();if(active)status.postDelayed(this::disconnect,autoDuration);}
    @Override protected void onResume(){super.onResume();foreground=true;if(audioManager!=null){refreshMedia();handler.post(mediaPoll);}beginAutoIfReady();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)beginAutoIfReady();}
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(mediaPoll);super.onPause();if(active && !closing){closing=true;receiver.stop();}}
    private void loadBook(){try{JSONArray a=new JSONArray(getSharedPreferences("p8_rx",0).getString("channels","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);book.add(new P8RxProtocol.Channel(o.getString("name"),o.getInt("hz"),o.getInt("bw"),o.getInt("sq"),o.getInt("monitor"),1));}}catch(Exception ignored){}
        if(book.isEmpty())book.add(new P8RxProtocol.Channel("P8 RX",446006250,0,1,2,1));}
    private boolean saveBook(){try{JSONArray a=new JSONArray();for(P8RxProtocol.Channel ch:book)a.put(new JSONObject().put("name",ch.name).put("hz",ch.hz).put("bw",ch.bandwidth).put("sq",ch.squelch).put("monitor",ch.monitor));boolean saved=getSharedPreferences("p8_rx",0).edit().putString("channels",a.toString()).commit();show(saved?"Saved RX channel list ("+book.size()+" channels)":"SAVE FAILED: RX channel list");return saved;}catch(Exception e){show("SAVE FAILED: "+e);return false;}}
}
