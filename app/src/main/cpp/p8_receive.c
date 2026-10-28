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
#include <pthread.h>
#ifndef P8_OPEN_PROBE_REVIEWED
#define P8_OPEN_PROBE_REVIEWED 0
#endif
#ifndef P8_TX_TEST_REVIEWED
#define P8_TX_TEST_REVIEWED 0
#endif
static void fail(JNIEnv *e,const char *message) { (*e)->ThrowNew(e,(*e)->FindClass(e,"java/io/IOException"),message); }
#if P8_OPEN_PROBE_REVIEWED
static int control=-1,pcm=-1,control_saved=0,pcm_saved=0;
static struct termios control_term,pcm_term;
static pthread_mutex_t tx_lock=PTHREAD_MUTEX_INITIALIZER;
static int tx_ready=0,tx_owned=0,tx_error=0,watch_running=0,watch_stop=0;
static pthread_t watch_thread;
static long tx_end;
static long last_pcm_write;
static uint32_t tuned_hz;
static int tuned_command;
static long millis(void) {struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000L+t.tv_nsec/1000000L;}
/* Only this fixed PTT node can be written; no power, audio, calibration or firmware API. */
static int ptt_write(int released){
    int fd=open("/sys/devices/platform/dmr_gpio/dmrptt",O_WRONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0)return -1;
    char value=released?'1':'0';ssize_t n=write(fd,&value,1);int rc=close(fd);return n==1 && rc==0?0:-1;
}
#if P8_TX_TEST_REVIEWED
static void *tx_watch(void *ignored){
    (void)ignored;
    for(;;){pthread_mutex_lock(&tx_lock);int stop=watch_stop;
        if(tx_owned && (stop || millis()>=tx_end)){if(ptt_write(1))tx_error=1;else tx_owned=0;}
        pthread_mutex_unlock(&tx_lock);if(stop)break;struct timespec delay={0,10000000};nanosleep(&delay,NULL);
    }return NULL;
}
#endif
static int shutdown_ports(void) {
    int error=0;
    pthread_mutex_lock(&tx_lock);watch_stop=1;pthread_mutex_unlock(&tx_lock);
    if(watch_running){pthread_join(watch_thread,NULL);watch_running=0;}
    pthread_mutex_lock(&tx_lock);if(tx_owned && ptt_write(1))error=EIO;else tx_owned=0;if(tx_error)error=EIO;tx_ready=0;pthread_mutex_unlock(&tx_lock);
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
static int radio_hz(uint32_t hz){return (hz>=136000000 && hz<=174000000)||(hz>=400000000 && hz<=480000000);}
static int radio_tone(unsigned int type,unsigned int code){return type<=3 && (type==0?code==0:type==1?code<=50:code<=82);}
static int valid_tune(unsigned char *b) {
    if(b[0]!=0x68 || b[1]!=0x23 || b[2]!=1 || b[3]!=1 || b[6]!=0 || b[7]!=19 || b[27]!=0x10)return 0;
    unsigned int ck=(b[4]<<8)|b[5];uint32_t sum=0;
    for(int i=0;i<28;i+=2)sum+=i==4?0:(unsigned int)((b[i]<<8)|b[i+1]);
    while(sum>>16)sum=(sum&65535)+(sum>>16);
    if((sum^65535)!=ck)return 0;
    uint32_t hz=le32(b+8);
    if(!radio_hz(hz) || !radio_hz(le32(b+12)))return 0;
    if(b[16]>1 || b[17]>1 || b[18]>9 || !radio_tone(b[19],b[20]) || !radio_tone(b[21],b[22]) || b[23]!=2 || b[24]<1 || b[24]>9 || b[25]<1 || b[25]>2 || b[26]!=b[25])return 0;
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
    else if(tcflush(pcm,TCIFLUSH)!=0){int rc=shutdown_ports();fail(e,rc?"PCM input flush and restoration failure":"Cannot discard stale PCM input at open");}
#else
    fail(e,"Receive transport disabled in this build");
#endif
}
/* Direct-mode, unencrypted DMR RX packet from stock w2.b.Q. No PTT or module maintenance commands. */
#if P8_OPEN_PROBE_REVIEWED
static int valid_digital(const unsigned char *b){
    if(b[0]!=0x68 || b[1]!=0x22 || b[2]!=1 || b[3]!=1 || b[6]!=0 || b[7]!=163 || b[171]!=0x10)return 0;
    uint32_t sum=0;for(int i=0;i<172;i+=2)sum+=i==4?0:(unsigned int)((b[i]<<8)|b[i+1]);
    while(sum>>16)sum=(sum&65535)+(sum>>16);
    if((sum^65535)!=((unsigned int)b[4]<<8|b[5]))return 0;
    uint32_t hz=le32(b+8);
    if(!((hz>=136000000 && hz<=174000000)||(hz>=400000000 && hz<=480000000)) || le32(b+12)!=hz)return 0;
    if(le32(b+16)<1 || le32(b+16)>0xffffff || le32(b+148)<1 || le32(b+148)>0xffffff)return 0;
    for(int i=20;i<148;i+=4)if(le32(b+i)>0xffffff)return 0;
    if(b[152]>2 || b[153]!=0 || b[154]>15 || b[155]>1 || b[156]>1 || b[157]!=0 || b[158]!=2)return 0;
    for(int i=159;i<167;i++)if(b[i]!=0)return 0;
    return b[167]==2 && b[168]>=1 && b[168]<=9 && b[169]==1 && b[170]==2;
}
#endif
JNIEXPORT jbyteArray JNICALL Java_com_macdmr_radio_P8ReceiveNative_tune(JNIEnv *e,jclass c,jbyteArray packet) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    unsigned char bytes[172];
    int length=(*e)->GetArrayLength(e,packet);
    if(control<0 || (length!=28 && length!=10 && length!=172)){fail(e,"Invalid receive command/session");return NULL;}
    (*e)->GetByteArrayRegion(e,packet,0,length,(jbyte*)bytes);
    int valid=length==28?valid_tune(bytes):length==172?valid_digital(bytes):0;
    if(length==10 && bytes[0]==0x68 && bytes[1]==0x2e && bytes[2]==1 && bytes[3]==1 && bytes[6]==0 && bytes[7]==1 && bytes[8]>=1 && bytes[8]<=9 && bytes[9]==0x10){
        unsigned int sum=0x682e + 0x0101 + 1 + ((unsigned int)bytes[8]<<8) + 0x10;
        while(sum>>16)sum=(sum&65535)+(sum>>16);
        valid=(sum^65535)==((unsigned int)bytes[4]<<8|bytes[5]);
    }
    if(!valid){fail(e,"Command rejected by receive-only native allowlist");return NULL;}
    pthread_mutex_lock(&tx_lock);int busy=tx_owned || tx_error;pthread_mutex_unlock(&tx_lock);
    if(busy){fail(e,"Cannot configure channel during TX or after PTT release failure");return NULL;}
    if(write(control,bytes,(size_t)length)!=length){fail(e,"Receive command write failure; no retry");return NULL;}
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
            if(reply[off+1]==bytes[1]){complete_ack=1;break;}off+=size;
        }
        if(complete_ack)break; /* Java validates full checksum/status; do not wait out the deadline. */
    }
    if(bytes[1]==0x23 || bytes[1]==0x22){
        /* Input captured before this channel's response belongs to startup/the previous channel.
           Discard only the host PCM receive queue; never flush control replies or transmit data. */
        if(tcflush(pcm,TCIFLUSH)!=0){fail(e,"Cannot discard pre-tune PCM input");return NULL;}
        tuned_hz=le32(bytes+8);tuned_command=bytes[1];
    }
    jbyteArray out=(*e)->NewByteArray(e,used);if(out && used)(*e)->SetByteArrayRegion(e,out,0,used,(jbyte*)reply);return out;
#else
    (void)packet;fail(e,"Receive transport disabled");return NULL;
#endif
}
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_prepareTransmit(JNIEnv *e,jclass c){
    (void)c;
#if P8_OPEN_PROBE_REVIEWED && P8_TX_TEST_REVIEWED
    if(control<0 || pcm<0 || tx_ready){fail(e,"Invalid TX preparation state");return;}
    /* Upgrade only the PCM descriptor, restoring its exact original attributes at close. No GPIO write. */
    struct stat before,after;if(fstat(pcm,&before)){fail(e,"PCM identity check failed");return;}
    int rw=open("/dev/ttyS0",O_RDWR|O_NOCTTY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);
    if(rw<0){fail(e,"Cannot open PCM UART for microphone output");return;}
    if(fstat(rw,&after) || before.st_rdev!=after.st_rdev || before.st_ino!=after.st_ino || before.st_dev!=after.st_dev){close(rw);fail(e,"PCM UART identity changed");return;}
    if(close(pcm)){close(rw);pcm=-1;fail(e,"UART restoration/close error during TX preparation");return;}pcm=rw;
    pthread_mutex_lock(&tx_lock);watch_stop=0;tx_error=0;pthread_mutex_unlock(&tx_lock);
    if(pthread_create(&watch_thread,NULL,tx_watch,NULL)){fail(e,"TX watchdog unavailable");return;}watch_running=1;tx_ready=1;last_pcm_write=0;
#else
    fail(e,"TX disabled in this build");
#endif
}
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_acknowledgeStatus(JNIEnv *e,jclass c){
    (void)c;
#if P8_OPEN_PROBE_REVIEWED && P8_TX_TEST_REVIEWED
    const unsigned char ack[10]={0x68,0x36,1,1,0x95,0xb7,0,1,1,0x10};
    if(control<0 || write(control,ack,sizeof ack)!=(ssize_t)sizeof ack)fail(e,"Status acknowledgement write failed; stop");
#else
    fail(e,"Status acknowledgement disabled in this build");
#endif
}
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_keyTimed(JNIEnv *e,jclass c,jboolean down,jint timeout){
    (void)c;
#if P8_OPEN_PROBE_REVIEWED && P8_TX_TEST_REVIEWED
    pthread_mutex_lock(&tx_lock);int error=0;
    if(down){
        if(!tx_ready || tx_owned || tx_error || !radio_hz(tuned_hz) || tuned_command!=0x23 || (timeout!=10000 && timeout!=30000 && timeout!=60000 && timeout!=120000 && timeout!=180000))error=1;
        else{tx_owned=1;tx_end=millis()+timeout;if(ptt_write(0)){error=1;if(ptt_write(1))tx_error=1;else tx_owned=0;}}
    }else if(tx_owned){if(ptt_write(1)){tx_error=1;error=1;}else tx_owned=0;}
    pthread_mutex_unlock(&tx_lock);if(error)fail(e,"PTT operation refused/failed; stop");
#else
    (void)down;(void)timeout;fail(e,"TX disabled in this build");
#endif
}
JNIEXPORT jlong JNICALL Java_com_macdmr_radio_P8ReceiveNative_writeMicrophone(JNIEnv *e,jclass c,jbyteArray data,jint length){
    (void)c;
#if P8_OPEN_PROBE_REVIEWED && P8_TX_TEST_REVIEWED
    if(length!=160 || (*e)->GetArrayLength(e,data)<length){fail(e,"TX PCM must be one 160-byte stock block");return 0;}
    unsigned char bytes[160];(*e)->GetByteArrayRegion(e,data,0,160,(jbyte*)bytes);
    /* Stock t2.b spaces every raw 160-byte write by >=10 ms, including buffered capture bursts.
       Keep the independent PTT watchdog running while this worker waits. */
    long delay=last_pcm_write+10-millis();
    if(delay>0){struct timespec pause={delay/1000,(delay%1000)*1000000L};while(nanosleep(&pause,&pause)<0 && errno==EINTR){}}
    pthread_mutex_lock(&tx_lock);int ok=tx_owned && !tx_error && millis()<tx_end;pthread_mutex_unlock(&tx_lock);
    if(!ok){fail(e,"TX PCM refused outside bounded keyed interval");return 0;}
    long sent=millis();ssize_t n=write(pcm,bytes,160);if(n!=160){fail(e,"TX PCM write failed/partial; no retry");return 0;}
    last_pcm_write=millis();return (jlong)sent;
#else
    (void)data;(void)length;fail(e,"TX disabled in this build");return 0;
#endif
}
JNIEXPORT jbyteArray JNICALL Java_com_macdmr_radio_P8ReceiveNative_volume(JNIEnv *e,jclass c,jint level) {
#if P8_OPEN_PROBE_REVIEWED
    if(level<1 || level>9){fail(e,"Module volume outside bounded development range");return NULL;}
    unsigned char bytes[10]={0x68,0x2e,1,1,0,0,0,1,(unsigned char)level,0x10};
    unsigned int sum=0x682e + 0x0101 + 1 + ((unsigned int)level<<8) + 0x10;
    while(sum>>16)sum=(sum&65535)+(sum>>16);
    unsigned int checksum=sum^65535;bytes[4]=(unsigned char)(checksum>>8);bytes[5]=(unsigned char)checksum;
    jbyteArray packet=(*e)->NewByteArray(e,10);if(!packet)return NULL;
    (*e)->SetByteArrayRegion(e,packet,0,10,(jbyte*)bytes);
    jbyteArray result=Java_com_macdmr_radio_P8ReceiveNative_tune(e,c,packet);(*e)->DeleteLocalRef(e,packet);return result;
#else
    (void)c;(void)level;fail(e,"Receive transport disabled");return NULL;
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
JNIEXPORT void JNICALL Java_com_macdmr_radio_P8ReceiveNative_requestRssi(JNIEnv *e,jclass c) {
    (void)c;
#if P8_OPEN_PROBE_REVIEWED
    /* Fixed read-RSSI packet only. Asynchronous: never block the audio owner awaiting a reply. */
    const unsigned char query[10]={0x68,0x32,1,1,0x95,0xbb,0,1,1,0x10};
    pthread_mutex_lock(&tx_lock);int blocked=tx_owned || tx_error;pthread_mutex_unlock(&tx_lock);
    if(control<0 || blocked){fail(e,"RSSI read refused during TX/closed session");return;}
    if(write(control,query,sizeof query)!=(ssize_t)sizeof query)fail(e,"RSSI read request failed; no retry");
#else
    fail(e,"Receive telemetry disabled");
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
