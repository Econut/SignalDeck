/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.serial.port;

import java.io.FileDescriptor;

/**
 * The same serial opener as {@link com.pri.prizeinterphone.serial.port.SerialPort}, for phones whose stock app
 * binds its library to this class name instead (Ulefone Armor 20WT: libserial_port_app.so in the stock app's
 * own lib folder, report 2026-10-24). Same native signatures; {@link com.macdmr.radio.VendorSerial} loads the library.
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
