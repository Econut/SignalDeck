/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * JNI bridge to Codec 2 (LGPL-2.1, see codec2/COPYING) for com.macdmr.radio.ham.Codec2.
 */
#include <jni.h>
#include <stdlib.h>
#include "codec2.h"

JNIEXPORT jlong JNICALL Java_com_macdmr_radio_ham_Codec2_create(JNIEnv *env, jclass c, jint mode) {
    return (jlong) (intptr_t) codec2_create(mode);
}

JNIEXPORT void JNICALL Java_com_macdmr_radio_ham_Codec2_destroy(JNIEnv *env, jclass c, jlong h) {
    if (h) codec2_destroy((struct CODEC2 *) (intptr_t) h);
}

JNIEXPORT jint JNICALL Java_com_macdmr_radio_ham_Codec2_samplesPerFrame(JNIEnv *env, jclass c, jlong h) {
    return codec2_samples_per_frame((struct CODEC2 *) (intptr_t) h);
}

JNIEXPORT jint JNICALL Java_com_macdmr_radio_ham_Codec2_bytesPerFrame(JNIEnv *env, jclass c, jlong h) {
    return codec2_bytes_per_frame((struct CODEC2 *) (intptr_t) h);
}

/* One frame of bits -> samplesPerFrame 8 kHz samples. */
JNIEXPORT void JNICALL Java_com_macdmr_radio_ham_Codec2_decode(JNIEnv *env, jclass c, jlong h, jbyteArray bits, jshortArray out) {
    jbyte *b = (*env)->GetByteArrayElements(env, bits, NULL);
    jshort *o = (*env)->GetShortArrayElements(env, out, NULL);
    codec2_decode((struct CODEC2 *) (intptr_t) h, (short *) o, (const unsigned char *) b);
    (*env)->ReleaseShortArrayElements(env, out, o, 0);
    (*env)->ReleaseByteArrayElements(env, bits, b, JNI_ABORT);
}

/* samplesPerFrame 8 kHz samples -> one frame of bits (bench and tests). */
JNIEXPORT void JNICALL Java_com_macdmr_radio_ham_Codec2_encode(JNIEnv *env, jclass c, jlong h, jshortArray in, jbyteArray bits) {
    jshort *s = (*env)->GetShortArrayElements(env, in, NULL);
    jbyte *b = (*env)->GetByteArrayElements(env, bits, NULL);
    codec2_encode((struct CODEC2 *) (intptr_t) h, (unsigned char *) b, (short *) s);
    (*env)->ReleaseByteArrayElements(env, bits, b, 0);
    (*env)->ReleaseShortArrayElements(env, in, s, JNI_ABORT);
}
