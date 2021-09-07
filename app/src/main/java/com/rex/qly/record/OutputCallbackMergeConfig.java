package com.rex.qly.record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

// Merge SPS & PPS into IDR frame
// But still leave config data there
public class OutputCallbackMergeConfig extends SurfaceRecorder.OutputCallbackWrapper {

    private final Logger mLogger = LoggerFactory.getLogger(OutputCallbackMergeConfig.class);

    private ByteBuffer mSPS;
    private ByteBuffer mPPS;

    public OutputCallbackMergeConfig(SurfaceRecorder.OutputCallback cb) {
        super(cb);
        mLogger.trace("");
    }

    @Override
    public void onConfig(ByteBuffer sps, ByteBuffer pps) {
        super.onConfig(sps, pps);
        sps.rewind();
        pps.rewind();
        mLogger.trace("sps:{} pps:{}", sps.remaining(), pps.remaining());
        if (!sps.isDirect()) {
            sps = ByteBuffer.allocateDirect(sps.remaining()).put(sps);
            sps.rewind();
        }
        if (!pps.isDirect()) {
            pps = ByteBuffer.allocateDirect(pps.remaining()).put(pps);
            pps.rewind();
        }
        // Cache SPS and PPS to merge with IDR
        mSPS = sps;
        mPPS = pps;
    }

    @Override
    public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
        mLogger.trace("offset:{} size:{} pts:{}", offset, size, pts);
        if (mSPS != null && mPPS != null) {
            mLogger.trace("sps:{} pps:{} buffer:{}", mSPS.remaining(), mPPS.remaining(), buffer.remaining());
            buffer = ByteBuffer.allocateDirect(mSPS.remaining() + mPPS.remaining() + buffer.remaining())
                    .put(mSPS)
                    .put(mPPS)
                    .put(buffer);
            buffer.rewind();
            offset = 0;
            size = buffer.remaining();
            mSPS = null;
            mPPS = null;
        }
        super.onFrame(buffer, offset, size, pts);
    }
}
