#define LOG_TAG     "libffmpegjni"
#define LOG_LEVEL   LOG_LEVEL_ALL

#include <algorithm>
#include <cinttypes>
#include <cstring>

#include <jni.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <libavutil/time.h>
}

#include "nalu.h"
#include "debug.h"
#include "logging.h"

//#define DUMP_H264_CODEC
//#define DUMP_H264_DATA

void __cb_ffmpeg_log(void * ptr, int level, const char * fmt, va_list vargs);

struct context {
    AVFormatContext *   fmt_ctx;
    AVCodecContext *    codec_ctx_video;
    AVCodecContext *    codec_ctx_audio;
    AVStream *          stream_video;
    AVStream *          stream_audio;
    const char *        url;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_rex_qly_FFmpeg_nativeCreate(JNIEnv* env, jclass clazz)
{
    av_log_set_callback(__cb_ffmpeg_log);
    av_log_set_level(AV_LOG_INFO);
    //av_log_set_level(AV_LOG_VERBOSE);

    av_register_all();
    avformat_network_init();

    auto ctx = new context();
    ctx->fmt_ctx = nullptr;
    ctx->codec_ctx_video = nullptr;
    ctx->codec_ctx_audio = nullptr;
    ctx->stream_video = nullptr;
    ctx->stream_audio = nullptr;
    LOGV("nativeCreate ctx:%p", ctx);
    return reinterpret_cast<intptr_t>(ctx);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rex_qly_FFmpeg_nativeInitVideo(JNIEnv * env, jclass clazz,
        jlong ptr, jint width, jint height, jint fps, jint bps)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return false;
    LOGV("nativeInitVideo+ ctx:%p width:%d height:%d fps:%d bps:%d", ctx, width, height, fps, bps);

    AVCodecContext * codec_ctx = avcodec_alloc_context3(avcodec_find_encoder(AV_CODEC_ID_H264));
    codec_ctx->codec_id     = AV_CODEC_ID_H264;
    codec_ctx->codec_type   = AVMEDIA_TYPE_VIDEO;
    codec_ctx->codec_tag    = 0;
    codec_ctx->pix_fmt      = AV_PIX_FMT_YUV420P;
    codec_ctx->width        = width;
    codec_ctx->height       = height; // Resolution must be a multiple of two
    codec_ctx->bit_rate     = bps;
    codec_ctx->framerate    = (AVRational) { fps, 1 };
    codec_ctx->time_base    = (AVRational) { 1, fps };
    codec_ctx->gop_size     = 12;
    //codec_ctx->profile      = FF_PROFILE_H264_BASELINE;
    //codec_ctx->level        = ;
    ctx->codec_ctx_video    = codec_ctx;

    LOGV("nativeInitVideo- codec_ctx:%p", codec_ctx);
    return true;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rex_qly_FFmpeg_nativeInitAudio(JNIEnv * env, jclass clazz,
        jlong ptr, jint sample_rate, jint sample_size, jint channels)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return false;
    LOGV("nativeInitAudio+ ctx:%p sample_rate:%d sample_size:%d channels:%d", ctx, sample_rate, sample_size, channels);

    AVCodecContext * codec_ctx = avcodec_alloc_context3(avcodec_find_encoder(AV_CODEC_ID_AAC));
    codec_ctx->codec_id     = AV_CODEC_ID_AAC;
    codec_ctx->codec_type   = AVMEDIA_TYPE_AUDIO;
    codec_ctx->codec_tag    = 0;
    codec_ctx->sample_fmt   = AV_SAMPLE_FMT_S16;
    codec_ctx->sample_rate  = sample_rate;
    codec_ctx->channels     = channels;
    ctx->codec_ctx_audio    = codec_ctx;

    LOGV("nativeInitAudio- codec_ctx:%p", codec_ctx);
    return true;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rex_qly_FFmpeg_nativeOpen(JNIEnv* env, jclass clazz, jlong ptr, jstring jurl)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return false;

    const char * url = env->GetStringUTFChars(jurl, nullptr);
    LOGV("nativeOpen+ ctx:%p url:<%s>", ctx, url);

//    int64_t ts = av_gettime_relative();   // uptime in microsecond, same timebase as pts
//    AVRational tb = av_get_time_base_q(); // 1/1000000
//    LOGV("nativeOpen ts:%" PRId64 " tb:%d/%d", ts, tb.num, tb.den);

    AVFormatContext * fmt_ctx = nullptr;
    avformat_alloc_output_context2(&fmt_ctx, nullptr, "flv", url);
    if (!fmt_ctx) {
        LOGW("Failed to allocate format context");
        goto exit;
    }
    ctx->fmt_ctx = fmt_ctx;
    ctx->url = strdup(url);

    if (ctx->codec_ctx_video) {
        fmt_ctx->oformat->video_codec = ctx->codec_ctx_video->codec_id;
//        AVStream * stream = avformat_new_stream(fmt_ctx, ctx->codec_ctx_video->codec);
        AVStream * stream = avformat_new_stream(fmt_ctx, nullptr);
        if (!stream) {
            LOGW("Failed to allocate output stream");
            goto exit;
        }
        stream->id = fmt_ctx->nb_streams - 1;
        //stream->time_base = ctx->codec_ctx_video->time_base; // 1/fps
        stream->time_base = (AVRational) { 1, 1000000 }; // pts in microseconds (10^-6)
        stream->avg_frame_rate = ctx->codec_ctx_video->framerate;

        AVCodecParameters * params = stream->codecpar;
        avcodec_parameters_from_context(params, ctx->codec_ctx_video);
        LOGD("AVCodecParameters codec_type:%d codec_id:%d codec_tag:%d profile:%d level:%d format:%d width:%d height:%d bit_rate:%ld",
             params->codec_type,
             params->codec_id,
             params->codec_tag,
             params->profile,
             params->level,
             params->format,
             params->width,
             params->height,
             params->bit_rate);

        if (fmt_ctx->oformat->flags & AVFMT_GLOBALHEADER) {
            stream->codec->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
        }
        ctx->stream_video = stream;
        LOGD("AVStream id:%d time_base:%d/%d", stream->id, stream->time_base.num, stream->time_base.den);
    }
    av_dump_format(fmt_ctx, 0, url, 1);

//    if (!(fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
//        int err = avio_open2(&fmt_ctx->pb, url, AVIO_FLAG_WRITE, nullptr, nullptr);
//        if (err < 0) {
//            char err_buf[1024];
//            av_strerror(err, err_buf, sizeof(err_buf));
//            LOGW("Failed to open '%s' - err:0x%08x(%s)", url, err, err_buf);
//            goto exit;
//        }
//        LOGI("Open url <%s>", url);
//    }

//    if (avformat_write_header(fmt_ctx, nullptr) < 0) {
//        LOGW("Failed to write header");
//        goto exit;
//    }

exit:
    if (jurl) env->ReleaseStringUTFChars(jurl, url);
    LOGV("nativeOpen- fmt_ctx:%p", fmt_ctx);
    return true;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_rex_qly_FFmpeg_nativeSendVideoCodec(JNIEnv * env, jclass clazz,
        jlong ptr, jobject jdata, jint offset, jint size)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return 0;
    if (!ctx->stream_video) return 0;

    auto data = (uint8_t *) env->GetDirectBufferAddress(jdata);
    LOGV("nativeSendVideoCodec+ ctx:%p fmt_ctx:%p data:%p offset:%d size:%d", ctx, ctx->fmt_ctx, data, offset, size);

#ifdef DUMP_H264_CODEC
    dump_buffer(data + offset, (size_t) size, "Codec");
#endif

    auto extradata = (uint8_t *) av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    ::memcpy(extradata, data, size);

    ctx->stream_video->codecpar->extradata = extradata;
    ctx->stream_video->codecpar->extradata_size = size;

    if (!(ctx->fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        int err = avio_open2(&ctx->fmt_ctx->pb, ctx->url, AVIO_FLAG_WRITE, nullptr, nullptr);
        if (err < 0) {
            char err_buf[1024];
            av_strerror(err, err_buf, sizeof(err_buf));
            LOGW("Failed to open '%s' - err:0x%08x(%s)", ctx->url, err, err_buf);
            goto exit;
        }
        LOGI("Open url <%s>", ctx->url);
    }

    if (avformat_write_header(ctx->fmt_ctx, nullptr) < 0) {
        LOGW("Failed to write header");
        goto exit;
    }
exit:

    LOGV("nativeSendVideoCodec- size:%d", size);
    return size;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_rex_qly_FFmpeg_nativeSendVideoData(JNIEnv * env, jclass clazz,
        jlong ptr, jobject jdata, jint offset, jint size, jlong pts)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return 0;
    if (!ctx->stream_video) return 0;

    auto data = (uint8_t *) env->GetDirectBufferAddress(jdata);
    //LOGV("nativeSendVideoData+ ctx:%p fmt_ctx:%p data:%p pts:%" PRId64 " offset:%d size:%d", ctx, ctx->fmt_ctx, data, pts, offset, size);

    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = data + offset;
    pkt.size = size;
    //pkt.pts = AV_NOPTS_VALUE;
    //pkt.pts = pts; // presentationTimeUs is microseconds (10^-6)
    pkt.pts = av_rescale_q(pts, { 1, 1000000 }, ctx->stream_video->time_base); // presentationTimeUs is microseconds (10^-6), scale to stream time base
    pkt.dts = pkt.pts;
    pkt.stream_index = ctx->stream_video->index;
    pkt.duration = 0; // XXX: In AVStream->time_base units, 0 if unknown
    LOGV("nativeSendVideoData pts:%" PRId64 " pkt.pts:%" PRId64 "(%.3f) pkt.dts:%" PRId64 "(%.3f)",
         pts,
         pkt.pts, av_q2d(ctx->stream_video->time_base) * pkt.pts,
         pkt.dts, av_q2d(ctx->stream_video->time_base) * pkt.dts);

    // XXX: Not necessary
    size_t pos = offset;
    while (true) {
        int type = nal_type(data + pos, size + offset - pos);
        //LOGV("nativeSendVideoData pos:%zu type:%d(%s)", pos, type, nal_type_str(type));
        switch (type) {
        case H264_NAL_SEI:
        case H264_NAL_PPS:
        case H264_NAL_SPS:
            pos += nal_parse(data + pos, size + offset - pos);
            continue;
        case H264_NAL_IDR_SLICE:
            //LOGV("nativeSendVideoData AV_PKT_FLAG_KEY");
            pkt.flags |= AV_PKT_FLAG_KEY;
            break;
        default:
            break;
        }
        break;
    }

#ifdef DUMP_H264_DATA
    dump_buffer(data + offset, (size_t) std::min(size, 64), "Data");
#endif

    if (av_interleaved_write_frame(ctx->fmt_ctx, &pkt) < 0) {
        LOGW("Failed to write frame");
    }

    //LOGV("nativeSendVideoData- size:%d", size);
    return size;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rex_qly_FFmpeg_nativeClose(JNIEnv * env, jclass clazz, jlong ptr)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return;
    LOGV("nativeClose ctx:%p fmt_ctx:%p", ctx, ctx->fmt_ctx);

    if (ctx->fmt_ctx) {
        av_write_trailer(ctx->fmt_ctx);
        if (!(ctx->fmt_ctx->flags & AVFMT_NOFILE)) {
            avio_close(ctx->fmt_ctx->pb);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rex_qly_FFmpeg_nativeRelease(JNIEnv* env, jclass clazz, jlong ptr)
{
    auto ctx = reinterpret_cast<context *>(ptr);
    if (!ctx) return;
    LOGV("nativeRelease ctx:%p", ctx);

    if (ctx->fmt_ctx) avformat_free_context(ctx->fmt_ctx);
    if (ctx->codec_ctx_video) avcodec_free_context(&ctx->codec_ctx_video);
    if (ctx->codec_ctx_audio) avcodec_free_context(&ctx->codec_ctx_audio);
    if (ctx->url) free((void *) ctx->url);
    avformat_network_deinit();
    delete ctx;
}

void
__cb_ffmpeg_log(void * ptr, int level, const char * fmt, va_list vargs)
{
    int l = LOG_LEVEL_DEBUG;
    if (level > av_log_get_level()) {
        return;
    }

    switch (level) {
    case AV_LOG_QUIET:      l = LOG_LEVEL_SILENT;   break;
    case AV_LOG_PANIC:
    case AV_LOG_FATAL:      l = LOG_LEVEL_FATAL;    break;
    case AV_LOG_ERROR:      l = LOG_LEVEL_ERROR;    break;
    case AV_LOG_WARNING:    l = LOG_LEVEL_WARN;     break;
    case AV_LOG_INFO:       l = LOG_LEVEL_INFO;     break;
    case AV_LOG_VERBOSE:    l = LOG_LEVEL_DEBUG;    break;
    case AV_LOG_DEBUG:
    default:                l = LOG_LEVEL_VERBOSE;  break;
    }
    LOG_VPRINT(l, fmt, vargs);
}

// vim:ts=4:sw=4:et: