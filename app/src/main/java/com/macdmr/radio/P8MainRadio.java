/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 main-interface adapter. See COPYRIGHT. */
package com.macdmr.radio;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import org.json.JSONArray;
import org.json.JSONObject;

/** Main radio channel mapping. Keeps the proven native RX boundary independent of generic/root transports. */
public final class P8MainRadio {
    private P8MainRadio(){}
    public static P8RxProtocol.Channel receiveChannel(ChannelStore.Channel ch,boolean monitor,int volume){
        if(ch==null || ch.type!=ChannelStore.Channel.TYPE_FM)throw new IllegalArgumentException("P8 DMR/M17 reception is not validated yet; choose an analogue FM channel");
        P8AnaloguePolicy.validate(ch.rxFreq,ch.txFreq,ch.power,ch.rxType,ch.rxSubCode,ch.txType,ch.txSubCode);
        P8RxProtocol.Channel wire=new P8RxProtocol.Channel(ch.name,ch.rxFreq,ch.band,ch.sq,monitor?1:2,volume);
        wire.txHz=ch.txFreq;wire.power=ch.power;wire.rxToneType=monitor?0:ch.rxType;wire.rxToneIndex=monitor?0:ch.rxSubCode;
        wire.txToneType=ch.txType;wire.txToneIndex=ch.txSubCode;wire.txAllowed=!ChannelStore.receiveOnly(ch);
        return wire;
    }
    public static P8RxProtocol.Channel channel(ChannelStore.Channel ch,boolean monitor,int volume,int localId){
        if(ch!=null && ch.type==ChannelStore.Channel.TYPE_DMR){if(ch.encryptSw!=2 || ch.mode!=0)throw new IllegalArgumentException("P8 DMR: direct mode with encryption off only");return P8RxProtocol.Channel.digital(ch.name,ch.rxFreq,localId,ch.groups,ch.txContact,ch.contactType,ch.cc,ch.inSlot,ch.outSlot,volume);}
        return receiveChannel(ch,monitor,volume);
    }
    /** Transactional and repeat-safe; leaves the original workbench channel book unchanged. */
    public static void migrate(Context c,ChannelStore store) throws Exception {
        if(c.getSharedPreferences("p8_rx",0).getBoolean("mainChannelsMigrated",false))return;
        JSONArray a=new JSONArray(c.getSharedPreferences("p8_rx",0).getString("channels","[]"));
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);boolean exists=false;
                for(ChannelStore.Channel old:store.all())if(old.type==1 && old.rxFreq==o.getInt("hz") && old.name.equals(o.getString("name")))exists=true;
                if(exists)continue;
                ChannelStore.Channel ch=new ChannelStore.Channel();ch.name=o.getString("name");ch.type=1;
                ch.rxFreq=ch.txFreq=o.getInt("hz");ch.band=o.getInt("bw");ch.sq=o.getInt("sq");ch.rxOnly=true;ch.power=0;
                receiveChannel(ch,false,1);if(store.insert(ch)<0)throw new Exception("Channel migration insert failed");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(!c.getSharedPreferences("p8_rx",0).edit().putBoolean("mainChannelsMigrated",true).commit())throw new Exception("Migration marker save failed");
    }
    /** Normal Android app cannot force-stop a different UID: use the existing Android release UI. */
    public static void takeOver(Context c,RadioService service){
        try{if((c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&android.content.pm.ApplicationInfo.FLAG_STOPPED)!=0){service.bringUp();return;}}
        catch(android.content.pm.PackageManager.NameNotFoundException e){android.widget.Toast.makeText(c,"Stock Intercom package is missing; cannot establish ownership",android.widget.Toast.LENGTH_LONG).show();return;}
        new android.app.AlertDialog.Builder(c).setTitle("Release Intercom")
            .setMessage("Intercom is still enabled. Tap Force stop on its Android App info page, then return here and choose Connect / Take over. This keeps the original app installed and available for Hand back.")
            .setPositiveButton("Open Intercom App info",(d,w)->c.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:com.wtwd.ucee.dmr"))))
            .setNegativeButton("Cancel",null).show();
    }
    public static void restoreIntercom(Context c){c.startActivity(new Intent().setComponent(new ComponentName("com.wtwd.ucee.dmr","com.wtwd.ucee.dmr.common.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
}
