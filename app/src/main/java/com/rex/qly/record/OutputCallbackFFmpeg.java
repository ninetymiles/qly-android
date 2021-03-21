package com.rex.qly.record;

import com.rex.qly.FFmpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class OutputCallbackFFmpeg implements SurfaceRecorder.OutputCallback {

    private final Logger mLogger = LoggerFactory.getLogger(OutputCallbackFFmpeg.class);

    private final FFmpeg mFFmpeg;

    public OutputCallbackFFmpeg() {
        mLogger.trace("");
        mFFmpeg = new FFmpeg();
    }

    public OutputCallbackFFmpeg initVideo(int width, int height, int fps, int bps) {
        mLogger.trace("width:{} height:{} fps:{} bps:{}", width, height, fps, bps);
        mFFmpeg.initVideo(width, height, fps, bps);
        return this;
    }

    public OutputCallbackFFmpeg open(String url) {
        mLogger.trace("url:<{}>", url);
        mFFmpeg.open(url);
        return this;
    }

    @Override
    public void onFormat(int width, int height, int fps, int bps) {
        mLogger.trace("width:{} height:{} fps:{} bps:{}", width, height, fps, bps);
    }

    @Override
    public void onConfig(ByteBuffer sps, ByteBuffer pps) {
        mLogger.trace("sps:{} pps:{}", sps.remaining(), pps.remaining());
        ByteBuffer buffer = ByteBuffer.allocateDirect(sps.remaining() + pps.remaining())
                .put(sps)
                .put(pps);
        buffer.rewind();
        mFFmpeg.sendVideoCodec(buffer, 0, buffer.remaining());
    }

    @Override
    public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
        mLogger.trace("offset:{} size:{} pts:{}", offset, size, pts);
        mFFmpeg.sendVideoData(buffer, offset, size, pts); // pts is microseconds(10^-6)
    }

    @Override
    public void onEnd() {
        mLogger.trace("");
        mFFmpeg.close();
    }
}
