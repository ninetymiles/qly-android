package com.rex.qly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class Rtmp implements Closeable {

    private final Logger mLogger = LoggerFactory.getLogger(Rtmp.class);

    private long mNativePtr;

    public Rtmp() {
        mLogger.trace("this:{} version:{}", hashCode(), nativeVersion());
    }

    @Override // Closeable
    public void close() {
        if (mNativePtr != 0) {
            mLogger.trace("this:{} nativePtr:{}", hashCode(), mNativePtr);
            nativeClose(mNativePtr);
            mNativePtr = 0;
        }
    }

    @Override // Object
    protected void finalize() throws Throwable {
        mLogger.trace("this:{}", hashCode());
        if (mNativePtr != 0) {
            close();
        }
    }

    public boolean open(String url) {
        mNativePtr = nativeOpen(url);
        mLogger.trace("this:{} url:{} nativePtr:{}", hashCode(), url, mNativePtr);
        return (mNativePtr != 0);
    }

    public boolean sendVideoConfig(ByteBuffer sps, ByteBuffer pps) {
        mLogger.trace("this:{} sps:{} pps:{}", hashCode(), sps.remaining(), pps.remaining());
        return (mNativePtr != 0 && nativeSendVideoConfig(mNativePtr, sps, pps) > 0);
    }

    public boolean sendVideoFrame(ByteBuffer data, int offset, int size, long pts) {
        mLogger.trace("this:{} offset:{} size:{} pts:{}", hashCode(), offset, size, pts);
        return (mNativePtr != 0 && nativeSendVideoData(mNativePtr, data, offset, size, pts) > 0);
    }

    private static native String nativeVersion();
    private static native long nativeOpen(String url);
    private static native int nativeSendVideoConfig(long ptr, ByteBuffer sps, ByteBuffer pps);
    private static native int nativeSendVideoData(long ptr, ByteBuffer data, int offset, int size, long pts);
    private static native void nativeClose(long ptr);

    static {
        System.loadLibrary("rtmp_jni");
    }
}
