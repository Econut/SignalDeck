/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.pri.prizeinterphone.serial.port;

import java.io.FileDescriptor;

/**
 * The stock walkie-talkie app's own serial opener (the classic android-serialport-api JNI: open the tty
 * O_RDWR, cfsetispeed/cfsetospeed + tcsetattr, hand back the fd). The library ships on /system of every
 * phone this radio family runs on, and its JNI symbols are bound to this exact class name, which is why the
 * class lives in the stock app's package. Using it means the line is configured the way the firmware was
 * tested with and needs no stty and no root: toybox's stty on the MT6577 UART reports success but leaves
 * the port at 9600 after a cold boot (measured 2026-10-17). {@link com.macdmr.radio.VendorSerial} loads the library.
 */
public final class SerialPort {
    private FileDescriptor mFd;            // read by the native close()

    private static native FileDescriptor open(String path, int baud, int flags);
    public native void close();

    /** Open and configure the line; null if the native open failed (the library logs why). */
    public static SerialPort openPort(String path, int baud) {
        SerialPort p = new SerialPort();
        p.mFd = open(path, baud, 0);
        return p.mFd == null ? null : p;
    }

    public FileDescriptor fd() { return mFd; }

    public void release() {
        try { if (mFd != null) close(); } catch (Throwable ignored) { }
        mFd = null;
    }
}
