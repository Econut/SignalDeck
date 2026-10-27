/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 development transport, 2026-10-27. See COPYRIGHT. */
package com.macdmr.radio;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.system.Os;
import android.util.Log;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Shell-only entry point (DUMP permission); no buttons, exported arbitrary bytes or auto-takeover.
 * Runs as ordinary lab app UID. Host stops/restores stock and attests process absence.
 */
public final class P8DiagnosticActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view=new TextView(this);view.setText("P8 bounded diagnostic; TX disabled");setContentView(view);
        if(state!=null){view.setText("Recreation: no diagnostic replay");return;}
        final String nonce=getIntent().getStringExtra("session");
        final int mode=getIntent().getIntExtra("mode",-1);
        final long observed=getIntent().getLongExtra("observed",-1);
        final boolean absent=getIntent().getBooleanExtra("stock_absent",false);
        new Thread(()->{
            JSONObject report=new JSONObject();
            try {
                report.put("uid",Os.getuid()).put("mode",mode).put("session",nonce);
                if(nonce==null || !nonce.matches("[a-zA-Z0-9_-]{8,64}"))throw new Exception("Invalid session");
                File result=new File(getFilesDir(),"p8-"+nonce+".json");
                if(!result.createNewFile())throw new Exception("Session already consumed; no replay");
                boolean stopped=(getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags & ApplicationInfo.FLAG_STOPPED)!=0;
                int ptt=readPtt();
                report.put("stockStopped",stopped).put("pttBefore",ptt)
                    .put("ownership","Known Intercom process released; other-holder exclusivity not proven");
                long started=SystemClock.elapsedRealtime();
                String r=P8RootFreeSession.run(stopped,absent,started-observed,ptt,mode);
                report.put("nativeResult",r).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                    .put("pttAfter",readPtt());
                if(mode==1 && r.startsWith("PASS")) {
                    int at=r.indexOf("rx=");
                    if(at<0)throw new Exception("Missing response bytes");
                    String captured=r.substring(at+3);
                    report.put("receivedHex",captured);
                    String version=P8Protocol.versionFromCapture(captured);
                    report.put("moduleVersion",version).put("frameValidation","PASS checksum, length, markers, status and ASCII");
                    getSharedPreferences("p8_diagnostic",MODE_PRIVATE).edit().putString("lastVersion",version)
                        .putLong("lastVersionAt",System.currentTimeMillis()).apply();
                }
                if(mode==2 && r.startsWith("PASS")) {
                    String hex=r.substring(r.indexOf("rx=")+3);
                    byte[] bytes=new byte[hex.length()/2];
                    for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
                    PcmFramer pcm=new PcmFramer();java.io.ByteArrayOutputStream payload=new java.io.ByteArrayOutputStream();
                    pcm.feed(bytes,bytes.length,(b,o,n)->payload.write(b,o,n));
                    report.put("capturedBytes",bytes.length).put("pcmFrames",pcm.frames).put("pcmPayloadBytes",payload.size())
                        .put("audioObservation",bytes.length==0?"No PCM bytes during bounded sample; RX/audio not proven":"Captured bytes; no audio playback or TX");
                    if(bytes.length>0)try(FileOutputStream f=new FileOutputStream(new File(getExternalFilesDir(null),"p8-"+nonce+".pcm"))){f.write(payload.toByteArray());}
                }
                getSharedPreferences("p8_diagnostic",MODE_PRIVATE).edit().putString("lastResult",report.toString()).apply();
            } catch(Exception | LinkageError e){try{report.put("error",e.toString());}catch(Exception ignored){}}
            String text=report.toString();Log.i("P8RootFree",text);
            if(nonce!=null && nonce.matches("[a-zA-Z0-9_-]{8,64}")){
                try(FileOutputStream f=new FileOutputStream(new File(getExternalFilesDir(null),"p8-"+nonce+".json"))){f.write(text.getBytes(StandardCharsets.UTF_8));}
                catch(Exception e){Log.e("P8RootFree","receipt write failed",e);}
            }
            runOnUiThread(()->{view.setText(text);finishAndRemoveTask();});
        },"P8-bounded-diagnostic").start();
    }
    private static int readPtt() throws Exception {
        try(FileInputStream f=new FileInputStream(P8Protocol.PTT_NODE)) {
            byte[] b=new byte[16];int n=f.read(b);
            return Integer.parseInt(new String(b,0,n,StandardCharsets.US_ASCII).trim());
        }
    }
}
