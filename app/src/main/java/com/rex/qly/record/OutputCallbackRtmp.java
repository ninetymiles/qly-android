package com.rex.qly.record;

import com.rex.qly.Rtmp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class OutputCallbackRtmp implements SurfaceRecorder.OutputCallback {

    private final Logger mLogger = LoggerFactory.getLogger(OutputCallbackRtmp.class);

    private final Rtmp mRtmp;

    public OutputCallbackRtmp(String url) {
        mLogger.trace("");
        mRtmp = new Rtmp();
        mRtmp.open(url);
    }

    @Override
    public void onFormat(int width, int height) {
        mLogger.trace("width:{} height:{}", width, height);
    }

    @Override
    public void onConfig(ByteBuffer sps, ByteBuffer pps) {
        mLogger.trace("sps:{} pps:{}", sps.remaining(), pps.remaining());
    }

    @Override
    public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
        mLogger.trace("offset:{} size:{} pts:{}", offset, size, pts);
    }

    @Override
    public void onEnd() {
        mLogger.trace("");
        mRtmp.close();
    }
}
