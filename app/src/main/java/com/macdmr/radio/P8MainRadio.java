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
        if(ch.rxType!=0 || ch.rxSubCode!=0)throw new IllegalArgumentException("P8 tone decoding is not validated yet; set receive tone to None");
        return new P8RxProtocol.Channel(ch.name,ch.rxFreq,ch.band,ch.sq,monitor?1:2,volume);
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
    public static void restoreIntercom(Context c){c.startActivity(new Intent().setComponent(new ComponentName("com.wtwd.ucee.dmr","com.wtwd.ucee.dmr.common.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
}
