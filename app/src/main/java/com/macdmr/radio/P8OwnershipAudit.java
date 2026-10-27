/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development adapter, 2026-10-27. See COPYRIGHT. */
package com.macdmr.radio;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.util.List;

/** Passive observations only. Never opens serial nodes, invokes a shell or changes an app.
 * Android app process lists are partial; not seeing Intercom cannot establish absence/ownership.
 */
public final class P8OwnershipAudit {
    private P8OwnershipAudit() { }
    public static String collect(Context c) {
        StringBuilder out = new StringBuilder("PASSIVE ROOT-FREE AUDIT — no UART opened; no stock-app action\n");
        try {
            ApplicationInfo app = c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0);
            out.append("stock package stopped flag: ").append((app.flags & ApplicationInfo.FLAG_STOPPED)!=0).append('\n');
            out.append("stock UID: ").append(app.uid).append(" (UID1000 is shared system identity; never kill by UID)\n");
        } catch(Exception e) { out.append("stock package state: UNKNOWN (not visible)\n"); }
        boolean seen=false;
        try {
            ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes=am==null?null:am.getRunningAppProcesses();
            if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)
                if("com.wtwd.ucee.dmr".equals(p.processName) || p.processName.startsWith("com.wtwd.ucee.dmr:")) seen=true;
        } catch(Exception ignored) { }
        out.append("stock process: ").append(seen?"RUNNING (visible)":"UNKNOWN (not visible; absence NOT established)").append('\n');
        for(String path:new String[]{P8Protocol.CONTROL_PORT,P8Protocol.PCM_PORT}) {
            File f=new File(path);out.append(path).append(" exists=").append(f.exists()).append(" read=").append(f.canRead()).append(" write=").append(f.canWrite()).append('\n');
        }
        out.append("UART ownership: UNKNOWN; no descriptor audit or exclusive access proven\n");
        out.append("UART open/close test: DISABLED pending review; no bytes/read/write/ioctl/GPIO test\n");
        out.append("Do not swipe Intercom away for a test: its task-removal handler writes module-power controls.\n");
        return out.toString();
    }
}
