package com.macdmr.radio;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Two bounded UTF-8 log files, retained across process exit and reboot. No PCM is recorded. */
public final class P8RecoveryJournal {
    private P8RecoveryJournal(){}
    private static boolean crashHandlerInstalled;
    public static synchronized void installCrashHandler(File dir){
        if(crashHandlerInstalled)return;
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        if(previous==null)return;
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));String text=trace.toString();append(dir,"UNCAUGHT "+thread.getName()+" "+text.substring(0,Math.min(text.length(),24000)));}catch(Throwable ignored){}
            finally{previous.uncaughtException(thread,error);}
        });
        crashHandlerInstalled=true;
    }
    static final long LIMIT=512*1024;
    public static synchronized void append(File dir,String event) throws IOException {
        if(!dir.exists() && !dir.mkdirs())throw new IOException("Cannot create recovery log folder");
        File file=new File(dir,"radio.log"),old=new File(dir,"radio-previous.log");
        if(file.length()>=LIMIT){if(old.exists() && !old.delete())throw new IOException("Cannot rotate previous log");if(!file.renameTo(old))throw new IOException("Cannot rotate radio log");}
        String line=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z",java.util.Locale.US).format(new java.util.Date())+" "+event.replace('\r',' ').replace('\n',' ') +"\n";
        try(FileOutputStream out=new FileOutputStream(file,true)){out.write(line.getBytes(StandardCharsets.UTF_8));}
    }
    public static synchronized String tail(File dir) throws IOException {
        File file=new File(dir,"radio.log");if(!file.exists())return "No recovery log yet";
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){long start=Math.max(0,in.length()-24000);in.seek(start);if(start>0)in.readLine();byte[] data=new byte[(int)(in.length()-in.getFilePointer())];in.readFully(data);return new String(data,StandardCharsets.UTF_8);}
    }
}
