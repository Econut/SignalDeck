/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development adapter, 2026-10-27. See COPYRIGHT. */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>

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
