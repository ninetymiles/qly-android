package com.rex.qly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rtmp {

    private final Logger mLogger = LoggerFactory.getLogger(Rtmp.class);

    public Rtmp() {
        mLogger.trace("this:{} version:{}", hashCode(), nativeVersion());
    }

    private static native String nativeVersion();

    static {
        System.loadLibrary("rtmp_jni");
    }
}
