/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive-only transport, 2026-10-27. See COPYRIGHT. */
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <termios.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <poll.h>
#include <errno.h>
#include <stdio.h>
#include <stdint.h>
#include <time.h>
#ifndef P8_OPEN_PROBE_REVIEWED
#define P8_OPEN_PROBE_REVIEWED 0
#endif
static void fail(JNIEnv *e,const char *message) { (*e)->ThrowNew(e,(*e)->FindClass(e,"java/io/IOException"),message); }
#if P8_OPEN_PROBE_REVIEWED
static int control=-1,pcm=-1,control_saved=0,pcm_saved=0;
static struct termios control_term,pcm_term;
static long millis(void) {struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000L+t.tv_nsec/1000000L;}
static int shutdown_ports(void) {
    int error=0;
    if(pcm>=0) { if(pcm_saved && tcsetattr(pcm,TCSANOW,&pcm_term)!=0)error=errno; if(close(pcm)!=0)error=errno;pcm=-1;pcm_saved=0; }
    if(control>=0) { if(control_saved && tcsetattr(control,TCSANOW,&control_term)!=0)error=errno; if(close(control)!=0)error=errno;control=-1;control_saved=0; }
    return error;
}
static int open_port(const char *path,int read_only,unsigned int maj,unsigned int min,speed_t baud,struct termios *original,int *saved) {
    struct stat before,after;
    if(lstat(path,&before)!=0 || !S_ISCHR(before.st_mode) || major(before.st_rdev)!=maj || minor(before.st_rdev)!=min)return -1;
    int fd=open(path,(read_only?O_RDONLY:O_RDWR)|O_NOCTTY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0)return -1;
    if(fstat(fd,&after)!=0 || before.st_rdev!=after.st_rdev || before.st_ino!=after.st_ino || before.st_dev!=after.st_dev || tcgetattr(fd,original)!=0) {close(fd);return -1;}
    struct termios t=*original;cfmakeraw(&t);t.c_cflag &= ~(CSIZE|PARENB|CSTOPB|CRTSCTS);t.c_cflag |= CS8|CREAD|CLOCAL;t.c_cc[VMIN]=0;t.c_cc[VTIME]=0;
    if(cfsetispeed(&t,baud)!=0 || cfsetospeed(&t,baud)!=0 || tcsetattr(fd,TCSANOW,&t)!=0) {int ignored=tcsetattr(fd,TCSANOW,original);(void)ignored;close(fd);return -1;}
    *saved=1;return fd;
}
static jbyteArray receive(JNIEnv *e,int fd,int timeout) {
    if(fd<0){fail(e,"RX session is closed");return NULL;}
    struct pollfd p={fd,POLLIN,0};unsigned char bytes[4096];
    int ready=poll(&p,1,timeout);
    if(ready<0 || (ready>0 && (p.revents&(POLLERR|POLLHUP|POLLNVAL)))) {fail(e,"RX poll failure; stop");return NULL;}
    ssize_t n=0;
    if(ready>0 && (p.revents&POLLIN)) {
        n=read(fd,bytes,sizeof bytes);
        if(n<0 && errno==EAGAIN)n=0;
        if(n<0){fail(e,"RX read failure; stop");return NULL;}
    }
    jbyteArray out=(*e)->NewByteArray(e,(jsize)n);
    if(out && n>0)(*e)->SetByteArrayRegion(e,out,0,(jsize)n,(jbyte*)bytes);
    return out;
}
static uint32_t le32(const unsigned char *b) {return (uint32_t)b[0]|(uint32_t)b[1]<<8|(uint32_t)b[2]<<16|(uint32_t)b[3]<<24;}
static int valid_tune(unsigned char *b) {
    if(b[0]!=0x68 || b[1]!=0x23 || b[2]!=1 || b[3]!=1 || b[6]!=0 || b[7]!=19 || b[27]!=0x10)return 0;
    unsigned int ck=(b[4]<<8)|b[5];uint32_t sum=0;
    for(int i=0;i<28;i+=2)sum+=i==4?0:(unsigned int)((b[i]<<8)|b[i+1]);
    while(sum>>16)sum=(sum&65535)+(sum>>16);
    if((sum^65535)!=ck)return 0;
    uint32_t hz=le32(b+8);
    if(!((hz>=136000000 && hz<=174000000)||(hz>=400000000 && hz<=480000000)) || le32(b+12)!=hz)return 0;
    if(b[16]>1 || b[17]!=0 || b[18]>9 || b[19] || b[20] || b[21] || b[22] || b[23]!=2 || b[24]<1 || b[24]>9 || b[25]<1 || b[25]>2 || b[26]!=b[25])return 0;
    return 1;
}
#endif
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_open(JNIEnv *e,jclass c) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    if(control>=0 || pcm>=0){fail(e,"RX session already open");return;}
    control=open_port("/dev/ttysWK0",0,207,5,B57600,&control_term,&control_saved);
    if(control<0){fail(e,"Cannot open command UART");return;}
    pcm=open_port("/dev/ttyS0",1,4,64,B230400,&pcm_term,&pcm_saved);
    if(pcm<0){int rc=shutdown_ports();fail(e,rc?"PCM open and restoration failure":"Cannot open PCM UART; command closed");}
#else
    fail(e,"Receive transport disabled in this build");
#endif
}
JNIEXPORT jbyteArray JNICALL Java_com_macdmr_radio_P8ReceiveNative_tune(JNIEnv *e,jclass c,jbyteArray packet) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    unsigned char bytes[28];
    if(control<0 || (*e)->GetArrayLength(e,packet)!=28){fail(e,"Invalid receive tune/session");return NULL;}
    (*e)->GetByteArrayRegion(e,packet,0,28,(jbyte*)bytes);
    if(!valid_tune(bytes)){fail(e,"Command rejected by receive-only native allowlist");return NULL;}
    if(write(control,bytes,28)!=28){fail(e,"Tune write failure; no retry");return NULL;}
    /* Accumulate response fragments without emitting ACKs or any other command. */
    unsigned char reply[4096];int used=0;long end=millis()+700;
    while(millis()<end && used<(int)sizeof reply) {
        struct pollfd p={control,POLLIN,0};int remaining=(int)(end-millis());if(remaining<=0)break;
        int n=poll(&p,1,remaining);
        if(n<0 || (n>0 && (p.revents&(POLLERR|POLLHUP|POLLNVAL)))){fail(e,"Tune response poll failure");return NULL;}
        if(!n)break;
        ssize_t got=read(control,reply+used,sizeof(reply)-(size_t)used);
        if(got<0){fail(e,"Tune response read failure");return NULL;}used+=(int)got;
        int off=0,complete_ack=0;
        while(used-off>=9 && reply[off]==0x68){
            int size=9+((reply[off+6]<<8)|reply[off+7]);
            if(size>1033 || used-off<size)break;
            if(reply[off+1]==0x23){complete_ack=1;break;}off+=size;
        }
        if(complete_ack)break; /* Java validates full checksum/status; do not wait out the deadline. */
    }
    jbyteArray out=(*e)->NewByteArray(e,used);if(out && used)(*e)->SetByteArrayRegion(e,out,0,used,(jbyte*)reply);return out;
#else
    (void)packet;fail(e,"Receive transport disabled");return NULL;
#endif
}
JNIEXPORT jbyteArray JNICALL Java_com_macdmr_radio_P8ReceiveNative_readPcm(JNIEnv *e,jclass c) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    return receive(e,pcm,50);
#else
    fail(e,"Receive transport disabled");return NULL;
#endif
}
JNIEXPORT jbyteArray JNICALL Java_com_macdmr_radio_P8ReceiveNative_readControl(JNIEnv *e,jclass c) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    return receive(e,control,0);
#else
    fail(e,"Receive transport disabled");return NULL;
#endif
}
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_close(JNIEnv *e,jclass c) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    if(shutdown_ports())fail(e,"UART restoration/close error; do not hand back automatically");
#else
    (void)e;
#endif
}
