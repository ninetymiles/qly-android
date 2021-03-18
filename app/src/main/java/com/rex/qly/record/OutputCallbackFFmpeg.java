package com.rex.qly.record;

import com.rex.qly.FFmpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class OutputCallbackFFmpeg implements SurfaceRecorder.OutputCallback {

    private final Logger mLogger = LoggerFactory.getLogger(OutputCallbackFFmpeg.class);

    private final FFmpeg mFFmpeg;

    public OutputCallbackFFmpeg(String url) {
        mLogger.trace("");
        mFFmpeg = new FFmpeg();
        mFFmpeg.open(url);
    }

    @Override
    public void onFormat(int width, int height) {
        mLogger.trace("width:{} height:{}", width, height);
    }

    @Override
    public void onConfig(ByteBuffer sps, ByteBuffer pps) {
        mLogger.trace("sps:{} pps:{}", sps.remaining(), pps.remaining());
        ByteBuffer buffer = ByteBuffer.allocateDirect(sps.remaining() + pps.remaining())
                .put(sps)
                .put(pps);
        buffer.rewind();
        mFFmpeg.sendVideoData(buffer, 0, buffer.remaining(), 0);
    }

    @Override
    public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
        mLogger.trace("offset:{} size:{} pts:{}", offset, size, pts);
        mFFmpeg.sendVideoData(buffer, offset, size, System.currentTimeMillis()); // timestamp use milliseconds(10^-3)
    }

    @Override
    public void onEnd() {
        mLogger.trace("");
        mFFmpeg.close();
    }
}
