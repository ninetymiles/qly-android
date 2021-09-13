package com.rex.qly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class FFmpeg implements Closeable {

    private final Logger mLogger = LoggerFactory.getLogger(FFmpeg.class);

    private final long mNativePtr; // An intptr_t for native struct context

    public FFmpeg() {
        mNativePtr = nativeCreate();
        mLogger.trace("this:{} nativePtr:{}", hashCode(), mNativePtr);
    }

    @Override // Closeable
    public void close() {
        if (mNativePtr != 0) {
            mLogger.trace("this:{} nativePtr:{}", hashCode(), mNativePtr);
            nativeClose(mNativePtr);
        }
    }

    @Override // Object
    protected void finalize() throws Throwable {
        mLogger.trace("this:{} nativePtr:{}", hashCode(), mNativePtr);
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
        }
    }

    public boolean initVideo(int width, int height, int fps, int bps) {
        mLogger.trace("this:{} nativePtr:{} width:{} height:{} fps:{} bps:{}", hashCode(), mNativePtr, width, height, fps, bps);
        return nativeInitVideo(mNativePtr, width, height, fps, bps);
    }

    public boolean initAudio(int sampleRate, int sampleSize, int channels) {
        mLogger.trace("this:{} nativePtr:{} sampleRate:{} sampleSize:{} channels:{}", hashCode(), mNativePtr, sampleRate, sampleSize, channels);
        return nativeInitAudio(mNativePtr, sampleRate, sampleSize, channels);
    }

    public boolean open(String url) {
        mLogger.trace("this:{} nativePtr:{} url:<{}>", hashCode(), mNativePtr, url);
        return nativeOpen(mNativePtr, url);
    }

    public boolean sendVideoCodec(ByteBuffer data, int offset, int size) {
        mLogger.trace("this:{} offset:{} size:{}", hashCode(), offset, size);
        return (mNativePtr != 0 && nativeSendVideoCodec(mNativePtr, data, offset, size) > 0);
    }

    public boolean sendVideoData(ByteBuffer data, int offset, int size, long pts) {
        //mLogger.trace("this:{} offset:{} size:{} pts:{}", hashCode(), offset, size, pts);
        return (mNativePtr != 0 && nativeSendVideoData(mNativePtr, data, offset, size, pts) > 0);
    }

    private static native long nativeCreate();
    private static native boolean nativeInitVideo(long ptr, int width, int height, int fps, int bps);
    private static native boolean nativeInitAudio(long ptr, int sampleRate, int sampleSize, int channels);
    private static native boolean nativeOpen(long ptr, String url);
    private static native int nativeSendVideoCodec(long ptr, ByteBuffer data, int offset, int size);
    private static native int nativeSendVideoData(long ptr, ByteBuffer data, int offset, int size, long pts);
    private static native void nativeClose(long ptr);
    private static native void nativeRelease(long ptr);

    static {
        System.loadLibrary("ffmpeg_jni");
    }
}
