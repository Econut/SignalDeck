/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 main radio controls. See COPYRIGHT. */
package com.macdmr.radio.ui;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.media.AudioManager;
import android.widget.*;
import com.macdmr.radio.RadioService;

/** Compact connection strip in the main radio page; audio settings are ordinary UI, not diagnostics. */
public final class P8RadioControls extends LinearLayout {
    private final RadioService service;
    private TextView note,state;private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){note.setText("P8 · ROOT-FREE · "+(service.p8TxArmed()?"ONE TX TEST ARMED":"TX LOCKED"));state.setText(service.p8Status());ui.postDelayed(this,500);}};
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.post(refresh);}
    @Override protected void onDetachedFromWindow(){ui.removeCallbacks(refresh);super.onDetachedFromWindow();}
    public P8RadioControls(Context c,RadioService s){super(c);service=s;setOrientation(VERTICAL);
        note=Neo.text(c,"P8 · ROOT-FREE · TX LOCKED",Neo.AMBER,12,Neo.ui(c));addView(note);
        state=Neo.text(c,service.p8Status(),Neo.MUTED,11,Neo.ui(c));state.setMaxLines(2);addView(state);
        LinearLayout row=new LinearLayout(c);addView(row);
        button(row,"Connect",service::bringUp);button(row,"Hand back",()->service.shutDown(true));button(row,"P8 controls",this::controls);
    }
    private void controls(){Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("P8 controls").setItems(new String[]{"Intercom App info / release","Receive volume","Arm one 2-second TX test","Programmable buttons"},(d,index)->{
        if(index==0){if(service.isReady()){toast("Hand back first");return;}c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:com.wtwd.ucee.dmr")));}
        else if(index==1)audio();else if(index==2)arm();else buttons();
    }).show();}
    private void arm(){Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("First transmit test — 446.10625 MHz")
            .setMessage("Only use the approved, authorised test setup. Fit the correct antenna or a correctly rated 50-ohm dummy load. Set the other receiver to Channel 9, narrow FM, no tones, and keep it far enough away to avoid feedback. Arming does not transmit. The next physical/on-screen PTT press is limited to two seconds. TX arm expires after 60 seconds and is never saved. DMR TX is disabled.")
            .setPositiveButton("Arm one test",(d,w)->toast(service.p8ArmTransmitTest())).setNegativeButton("Cancel",null).show();
    }
    private void toast(String text){Toast.makeText(getContext(),text,Toast.LENGTH_LONG).show();}
    private void buttons(){service.p8CancelTransmitTest();Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("Foreground programmable buttons").setItems(new String[]{"Assign button 1","Assign button 2","Clear assignments"},(d,which)->{
        if(which==2){c.getSharedPreferences("p8_buttons",0).edit().clear().commit();toast("Saved: button assignments cleared");return;}
        new android.app.AlertDialog.Builder(c).setTitle("Button "+(which+1)+" action").setItems(new String[]{"None","Next channel","Previous channel","Scan start/stop","Monitor on/off"},(a,action)->{
            if(action==0){c.getSharedPreferences("p8_buttons",0).edit().remove("key"+which).remove("action"+which).commit();toast("Saved: assignment removed");return;}
            final android.app.AlertDialog learn=new android.app.AlertDialog.Builder(c).setTitle("Press the programmable button").setMessage("Use a programmable key, not PTT, SOS or Power. This learns Android F/gamepad key events while the radio screen is foreground. TX must not be armed.").setNegativeButton("Cancel",null).create();
            com.macdmr.radio.P8ButtonBindings.learning=e->{if(e.getAction()!=android.view.KeyEvent.ACTION_DOWN || e.getRepeatCount()!=0)return;
                if(!com.macdmr.radio.P8ButtonBindings.acceptable(e.getKeyCode())){toast("That key is reserved; choose a programmable function key");return;}
                android.content.SharedPreferences p=c.getSharedPreferences("p8_buttons",0);android.content.SharedPreferences.Editor edit=p.edit();int other=1-which;if(p.getInt("key"+other,-1)==e.getKeyCode())edit.remove("key"+other).remove("action"+other);
                boolean ok=edit.putInt("key"+which,e.getKeyCode()).putInt("action"+which,action).commit();toast(ok?"Saved button "+(which+1):"Save failed");learn.dismiss();};
            learn.setOnKeyListener((d2,key,e)->{if(key==android.view.KeyEvent.KEYCODE_BACK)return false;java.util.function.Consumer<android.view.KeyEvent> capture=com.macdmr.radio.P8ButtonBindings.learning;if(capture!=null)capture.accept(e);return true;});
            learn.setOnDismissListener(x->com.macdmr.radio.P8ButtonBindings.learning=null);learn.show();
        }).show();
    }).show();}
    private void button(LinearLayout row,String title,Runnable run){TextView b=Neo.button(getContext(),title,"",Neo.CYAN);b.setTextSize(11);b.setSingleLine(true);row.addView(b,new LayoutParams(0,Theme.dp(getContext(),44),1));b.setOnClickListener(v->run.run());}
    private void audio(){Context c=getContext();LinearLayout body=new LinearLayout(c);body.setOrientation(VERTICAL);body.setPadding(24,12,24,12);
        TextView notice=new TextView(c);notice.setText("Saved controls apply on Connect; module and gain also apply during RX.");body.addView(notice);
        AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        slider(body,"Module receive level",1,9,service.p8ModuleVolume(),v->notice.setText(service.p8SaveAudio("moduleVolume",v)));
        slider(body,"Android media volume",0,am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),am.getStreamVolume(AudioManager.STREAM_MUSIC),v->{am.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);notice.setText(service.p8SaveAudio("mediaVolume",v));});
        slider(body,"Software gain ×",1,4,service.p8Gain(),v->notice.setText(service.p8SaveAudio("softwareGain",v)));
        TextView meter=new TextView(c);body.addView(meter);
        android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable tick=new Runnable(){public void run(){meter.setText(service.p8AudioMeter());handler.postDelayed(this,500);}};handler.post(tick);
        new android.app.AlertDialog.Builder(c).setTitle("Receive volume").setView(body).setPositiveButton("Done",null).setOnDismissListener(d->handler.removeCallbacks(tick)).show();
    }
    private interface Value{void set(int value);}
    private void slider(LinearLayout body,String title,int min,int max,int value,Value save){TextView label=new TextView(getContext());label.setText(title+": "+value);body.addView(label);SeekBar bar=new SeekBar(getContext());bar.setMax(max-min);bar.setProgress(value-min);body.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){label.setText(title+": "+(n+min));}public void onStopTrackingTouch(SeekBar b){save.set(b.getProgress()+min);}});
    }
}
