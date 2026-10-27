/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development adapter, 2026-10-27. See COPYRIGHT. */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>
#include <termios.h>
#include <poll.h>
#include <time.h>
#include <string.h>

#ifndef P8_OPEN_PROBE_REVIEWED
#define P8_OPEN_PROBE_REVIEWED 0
#endif

JNIEXPORT jstring JNICALL
Java_com_macdmr_radio_P8OpenProbe_nativeOpenAndClose(JNIEnv *env, jclass cls) {
    (void)cls;
#if !P8_OPEN_PROBE_REVIEWED
    return (*env)->NewStringUTF(env, "DISABLED: native UART open not reviewed; no node opened");
#else
    /* Hard-coded observed character device. No arbitrary path, bytes, termios, flush, lock or GPIO API. */
    const char *path = "/dev/ttysWK0";
    struct stat before, after;
    char result[192];
    if (lstat(path, &before) != 0) {
        snprintf(result, sizeof result, "FAIL lstat errno=%d", errno);
    } else if (!S_ISCHR(before.st_mode) || major(before.st_rdev) != 207 || minor(before.st_rdev) != 5) {
        snprintf(result, sizeof result, "STOP unexpected node type or device number");
    } else {
        int fd = open(path, O_RDWR | O_NOCTTY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0) {
            snprintf(result, sizeof result, "FAIL open errno=%d; no retry", errno);
        } else {
            int ok = fstat(fd, &after) == 0 && S_ISCHR(after.st_mode) && before.st_rdev == after.st_rdev
                     && before.st_dev == after.st_dev && before.st_ino == after.st_ino;
            int rc = close(fd), close_error = rc == 0 ? 0 : errno;
            snprintf(result, sizeof result, "%s open/fstat; close_errno=%d; zero bytes read/written; zero ioctls",
                     ok && rc == 0 ? "PASS" : "STOP", close_error);
        }
    }
    return (*env)->NewStringUTF(env, result);
#endif
}

#if P8_OPEN_PROBE_REVIEWED
static long monotonic_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000L + t.tv_nsec / 1000000L;
}
#endif

/* No generic send API: exactly open-only or the stock version request. Never ACK unsolicited packets. */
JNIEXPORT jstring JNICALL
Java_com_macdmr_radio_P8RootFreeSession_nativeRun(JNIEnv *env, jclass cls, jint mode) {
    (void)cls;
#if !P8_OPEN_PROBE_REVIEWED
    (void)mode;
    return (*env)->NewStringUTF(env,"DISABLED");
#else
    char result[4600]="STOP", error[160]="";
    struct stat before, after;
    struct termios saved, configured;
    int fd=-1, changed=0;
    unsigned char rx[2048]; size_t used=0;
    if(mode!=0 && mode!=1 && mode!=2)return (*env)->NewStringUTF(env,"STOP operation not allowlisted");
    const char *path=mode==2?"/dev/ttyS0":"/dev/ttysWK0";
    unsigned int expected_major=mode==2?4:207,expected_minor=mode==2?64:5;
    if(lstat(path,&before)!=0 || !S_ISCHR(before.st_mode)
       || major(before.st_rdev)!=expected_major || minor(before.st_rdev)!=expected_minor)
        return (*env)->NewStringUTF(env,"STOP unexpected UART node");
    fd=open(path,(mode==2?O_RDONLY:O_RDWR)|O_NOCTTY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0){snprintf(result,sizeof result,"STOP open errno=%d",errno);return (*env)->NewStringUTF(env,result);}
    if(fstat(fd,&after)!=0 || before.st_rdev!=after.st_rdev || before.st_dev!=after.st_dev || before.st_ino!=after.st_ino) {
        snprintf(error,sizeof error,"identity changed");goto cleanup;
    }
    if(mode==0)goto cleanup;
    if(tcgetattr(fd,&saved)!=0){snprintf(error,sizeof error,"tcgetattr errno=%d",errno);goto cleanup;}
    configured=saved;cfmakeraw(&configured);
    configured.c_cflag &= ~(CSIZE|PARENB|CSTOPB|CRTSCTS);
    configured.c_cflag |= CS8|CLOCAL|CREAD;
    configured.c_cc[VMIN]=0;configured.c_cc[VTIME]=0;
    speed_t baud=mode==2?B230400:B57600;
    if(cfsetispeed(&configured,baud)!=0 || cfsetospeed(&configured,baud)!=0){snprintf(error,sizeof error,"baud errno=%d",errno);goto cleanup;}
    /* Mark before setting, so an ambiguous failure still attempts original-setting restoration. */
    changed=1;
    if(tcsetattr(fd,TCSANOW,&configured)!=0){snprintf(error,sizeof error,"tcsetattr errno=%d",errno);goto cleanup;}
    if(mode==1) {
        const unsigned char query[]={0x68,0x34,0x01,0x01,0x95,0xb9,0x00,0x01,0x01,0x10};
        ssize_t sent=write(fd,query,sizeof query);
        if(sent!=(ssize_t)sizeof query){snprintf(error,sizeof error,"single write count=%ld errno=%d; no retry",(long)sent,sent<0?errno:0);goto cleanup;}
    }
    long deadline=monotonic_ms()+1500;
    while(monotonic_ms()<deadline && used<sizeof rx) {
        struct pollfd p={fd,POLLIN,0};
        int remaining=(int)(deadline-monotonic_ms());if(remaining<=0)break;
        int ready=poll(&p,1,remaining);
        if(ready<0){snprintf(error,sizeof error,"poll errno=%d",errno);break;}
        if(ready==0)break;
        if(p.revents&(POLLERR|POLLHUP|POLLNVAL)){snprintf(error,sizeof error,"poll events=%d",p.revents);break;}
        if(p.revents&POLLIN) {
            ssize_t n=read(fd,rx+used,sizeof rx-used);
            if(n>0)used+=(size_t)n;
            else if(n<0 && errno!=EAGAIN){snprintf(error,sizeof error,"read errno=%d",errno);break;}
        }
    }
    if(mode==1 && !error[0] && used==0)snprintf(error,sizeof error,"response timeout; no retry");
cleanup:
    if(changed && tcsetattr(fd,TCSANOW,&saved)!=0)snprintf(error,sizeof error,"restore termios errno=%d",errno);
    if(close(fd)!=0)snprintf(error,sizeof error,"close errno=%d",errno);
    if(error[0])snprintf(result,sizeof result,"STOP %s; rx_bytes=%zu",error,used);
    else if(mode==0)snprintf(result,sizeof result,"PASS open/fstat/close; zero serial bytes; zero termios/ioctls");
    else {
        int n=snprintf(result,sizeof result,"PASS %s; tx_bytes=%d; settings restored; rx=",mode==2?"PCM capture":"transport",mode==2?0:10);
        for(size_t i=0;i<used && n<(int)sizeof(result)-3;i++)n+=snprintf(result+n,sizeof(result)-(size_t)n,"%02X",rx[i]);
    }
    return (*env)->NewStringUTF(env,result);
#endif
}
