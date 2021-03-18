package com.rex.qly.record;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

// Ref to http://developer.android.com/guide/appendix/media-formats.html
// AVC  720P suggest  2Mbps
//     1080P suggest  5Mbps
//     2160P suggest 25Mbps
@TargetApi(Build.VERSION_CODES.KITKAT)
public class SurfaceRecorder {

    private final Logger mLogger = LoggerFactory.getLogger(SurfaceRecorder.class);

    private MediaCodec mCodec;
    private Surface mInputSurface;
    private SurfaceCallback mSurfaceCallback;
    private OutputCallback mOutputCallback;

    public interface SurfaceCallback {
        void onSurface(Surface surface);
    }

    public interface OutputCallback {
        void onFormat(int width, int height, int fps, int bps);
        void onConfig(ByteBuffer sps, ByteBuffer pps);
        void onFrame(ByteBuffer buffer, int offset, int size, long pts);
        void onEnd();
    }

    public SurfaceRecorder setSurfaceCallback(SurfaceCallback cb) {
        mSurfaceCallback = cb;
        return this;
    }

    public SurfaceRecorder setOutputCallback(OutputCallback cb) {
        mOutputCallback = cb;
        return this;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public synchronized SurfaceRecorder start(int width, int height, int frameRate, int bitRate) {
        mLogger.trace("+ width:{} height:{} frameRate:{} bitRate:{}", width, height, frameRate, bitRate);

        try {
            mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mLogger.info("Codec {}", mCodec.getName());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create codec\n", ex);
        }

        if (frameRate <= 0) frameRate = 30;
        int codecProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
        int codecLevel = MediaCodecInfo.CodecProfileLevel.AVCLevel31; // 3.1 for 720P
        if (height >= 1080) codecLevel = MediaCodecInfo.CodecProfileLevel.AVCLevel4; // 4.0 for 1080P
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                MediaCodecInfo codecInfo = mCodec.getCodecInfo();
                MediaCodecInfo.CodecCapabilities codecCapabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
                MediaCodecInfo.VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();

                // Calculate height first, 720P, 1080P, all base on the lines height
                height = Math.max(videoCapabilities.getSupportedHeights().getLower(), align(height, videoCapabilities.getHeightAlignment()));
                width  = Math.max(videoCapabilities.getSupportedWidthsFor(height).getLower(), align(width, videoCapabilities.getWidthAlignment()));

                Range<Double> range = videoCapabilities.getSupportedFrameRatesFor(width, height);
                frameRate = Math.max(frameRate, range.getLower().intValue());
                frameRate = Math.min(frameRate, range.getUpper().intValue());

                bitRate = Math.max(bitRate, videoCapabilities.getBitrateRange().getLower());
                bitRate = Math.min(bitRate, videoCapabilities.getBitrateRange().getUpper());

                MediaCodecInfo.CodecProfileLevel[] codecProfileLevels = codecCapabilities.profileLevels;
                if ((codecProfileLevels != null) && (codecProfileLevels.length != 0)) {
                    for (MediaCodecInfo.CodecProfileLevel profileLevel : codecProfileLevels) {
                        if (codecProfile == profileLevel.profile) {
                            codecLevel = Math.min(codecLevel, profileLevel.level);
                        }
                    }
                }
            } catch (Throwable tr) { // Some device not implement
                mLogger.warn("Failed to get codec capability\n", tr);
            }
        }

        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fmt.setInteger(MediaFormat.KEY_PROFILE, codecProfile);
            fmt.setInteger(MediaFormat.KEY_LEVEL, codecLevel);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fmt.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000); // microseconds
        }
        mLogger.debug("Codec config format <{}>", fmt);

        try {
            mCodec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mCodec.createInputSurface();
            if (mSurfaceCallback != null) {
                mSurfaceCallback.onSurface(mInputSurface);
            }
            mCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                }
                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    mLogger.trace("index:{} flags:{} offset:{} size:{} presentationTimeUs:{}", index, info.flags, info.offset, info.size, info.presentationTimeUs);
                    ByteBuffer outBuffer = codec.getOutputBuffer(index); // DirectByteBuffer
                    if (outBuffer != null) {
                        outBuffer.position(info.offset);
                        outBuffer.limit(info.offset + info.size);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) > 0) { // pts may be zero
                            mLogger.info("Got CODEC_CONFIG - {}", outBuffer.remaining());
                            //mLogger.debug("<{}>", Debug.dumpByteBuffer(outBuffer, info.offset, info.size));
                            // Nexus9 may provide valid PTS, Pixel may provide zero PTS
                            // e.g. 00 00 00 01 67 42 C0 20 E9 01 78 13 BC B2 C0 3C 22 11 A8 00 00 00 01 68 CE 06 E2
                            //if (mOutputCallback != null) {
                            //    mOutputCallback.onConfig(outBuffer, info.offset, info.size);
                            //}
                        } else {
                            // FIXME: Some device may auto include SPS and PPS in IDR frame, need remove it manually
                            // FIXME: Convert Annex-B to Avcc, avoid mixing with SEI frame still contain csd
                            mLogger.info("Got {} - {}", (((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) ? "KEY_FRAME" : "FRAME"), outBuffer.remaining());
                            //mLogger.debug("<{}>", Debug.dumpByteBuffer(outBuffer, info.offset, Math.min(info.size, 64)));
                            if (mOutputCallback != null) {
                                mOutputCallback.onFrame(outBuffer, info.offset, info.size, info.presentationTimeUs);
                            }
                        }
                        codec.releaseOutputBuffer(index, false);
                    }
                }
                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    mLogger.warn("Failed to process - {}", e.getMessage());
                    if (mOutputCallback != null) {
                        mOutputCallback.onEnd();
                    }
                }
                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    mLogger.debug("Codec output format <{}>", format);
                    int width  = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
                        width = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
                    }
                    if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
                        height = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
                    }
                    int frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    int bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    mLogger.debug("Codec output size {}x{}@{}({})", width, height, frameRate, bitRate);
                    if (mOutputCallback != null) {
                        mOutputCallback.onFormat(width, height, frameRate, bitRate);
                    }

                    ByteBuffer spsBuffer = format.getByteBuffer("csd-0"); // HeapByteBuffer
                    ByteBuffer ppsBuffer = format.getByteBuffer("csd-1"); // HeapByteBuffer
                    //mLogger.debug("SPS:<{}>", Debug.dumpByteBuffer(spsBuffer, 0, spsBuffer.remaining()));
                    //mLogger.debug("PPS:<{}>", Debug.dumpByteBuffer(ppsBuffer, 0, ppsBuffer.remaining()));
                    if (mOutputCallback != null) {
                        mOutputCallback.onConfig(spsBuffer, ppsBuffer);
                    }
                }
            });
            mCodec.start();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to configure and start MediaCodec\n", ex);
        }
        mLogger.trace("-");
        return this;
    }

    public synchronized SurfaceRecorder stop() {
        mLogger.trace("+");
        if (mCodec == null) {
            mLogger.trace("- not configure yet");
            return this;
        }

        // Detach surface from VirtualDisplay before signal codec EOF, avoid continue drawing on it may raise IllegalStateException
        if (mSurfaceCallback != null) {
            mSurfaceCallback.onSurface(null);
        }
        try {
            // Will stop surface submitting data immediately
            // XXX: Nexus10 signalEndOfInputStream may crash the mediaserver by SIGSEGV
            mCodec.signalEndOfInputStream();
            mCodec.stop();
            mCodec.release();
        } catch (Exception ex) {
            mLogger.warn("Failed to stop and release codec\n", ex);
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mOutputCallback != null) {
            mOutputCallback.onEnd();
        }
        mLogger.trace("-");
        return this;
    }

    public static class OutputCallbackWrapper implements OutputCallback {

        private final OutputCallback mDelegate;

        public OutputCallbackWrapper(OutputCallback cb) {
            mDelegate = cb;
        }

        @CallSuper
        @Override
        public void onFormat(int width, int height, int fps, int bps) {
            if (mDelegate != null) {
                mDelegate.onFormat(width, height, fps, bps);
            }
        }

        @CallSuper
        @Override
        public void onConfig(ByteBuffer sps, ByteBuffer pps) {
            if (mDelegate != null) {
                mDelegate.onConfig(sps, pps);
            }
        }

        @CallSuper
        @Override
        public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
            if (mDelegate != null) {
                mDelegate.onFrame(buffer, offset, size, pts);
            }
        }

        @CallSuper
        @Override
        public void onEnd() {
            if (mDelegate != null) {
                mDelegate.onEnd();
            }
        }
    }

    // Dump encoded H264 stream to sdcard
    public static class FileOutputCallbackWrapper extends OutputCallbackWrapper {

        private FileOutputStream mFileStream;
        private DataOutputStream mDataStream;

        private byte[] mData;
        private int mDataSize;

        public FileOutputCallbackWrapper(OutputCallback cb) {
            super(cb);

            long currentTime = System.currentTimeMillis() / 1000;
            String fileName  = "qly_" + currentTime + ".h264";
            String indexName = "qly_" + currentTime + ".index";
            try {
                mFileStream = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), fileName));
                mDataStream = new DataOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), indexName)));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onConfig(ByteBuffer sps, ByteBuffer pps) {
            try {
                mDataSize = sps.remaining() + pps.remaining();
                if (mData == null || mData.length < mDataSize) {
                    mData = new byte[mDataSize];
                }
                sps.mark();
                sps.get(mData, 0, sps.remaining());
                sps.reset();

                pps.mark();
                pps.get(mData, sps.remaining(), pps.remaining());
                pps.reset();

                if (mDataStream != null) {
                    mDataStream.writeInt(mDataSize);
                    mDataStream.writeLong(0);
                }
                if (mFileStream != null) {
                    mFileStream.write(mData, 0, mDataSize);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            super.onConfig(sps, pps);
        }

        @Override
        public void onFrame(ByteBuffer buffer, int offset, int size, long pts) {
            try {
                mDataSize = buffer.remaining();
                if (mData == null || mData.length < mDataSize) {
                    mData = new byte[mDataSize];
                }
                buffer.mark();
                buffer.get(mData, 0, mDataSize);
                buffer.reset();

                if (mDataStream != null) {
                    mDataStream.writeInt(mDataSize);
                    mDataStream.writeLong(pts);
                }
                if (mFileStream != null) {
                    mFileStream.write(mData, 0, mDataSize);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            super.onFrame(buffer, offset, size, pts);
        }

        @Override
        public void onEnd() {
            try {
                if (mDataStream != null) {
                    mDataStream.close();
                }
                if (mFileStream != null) {
                    mFileStream.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            super.onEnd();
        }
    }

    private int align(int value, int alignment) {
        return (value + (alignment -1) & ~(alignment - 1));
    }
}
