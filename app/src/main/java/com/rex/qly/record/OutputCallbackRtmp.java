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
    public void onFormat(int width, int height, int fps, int bps) {
        mLogger.trace("width:{} height:{} fps:{} bps:{}", width, height, fps, bps);
    }

    @Override
    public void onConfig(ByteBuffer sps, ByteBuffer pps) {
        mLogger.trace("sps:{} pps:{}", sps.remaining(), pps.remaining());
        if (!sps.isDirect()) {
            sps = ByteBuffer.allocateDirect(sps.remaining()).put(sps);
            sps.rewind();
        }
        if (!pps.isDirect()) {
            pps = ByteBuffer.allocateDirect(pps.remaining()).put(pps);
            pps.rewind();
        }
        mRtmp.sendVideoConfig(sps, pps);
    }

    @Override
    public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
        //mLogger.trace("offset:{} size:{} pts:{}", offset, size, pts);
        mRtmp.sendVideoFrame(buffer, offset, size, System.currentTimeMillis()); // timestamp use milliseconds(10^-3)
    }

    @Override
    public void onEnd() {
        mLogger.trace("");
        mRtmp.close();
    }
}
