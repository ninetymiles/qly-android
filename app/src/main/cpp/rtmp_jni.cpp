#define LOG_TAG     "librtmpjni"
//#define LOG_LEVEL   LOG_LEVEL_ALL

#include <algorithm>
#include <cinttypes>
#include <cstring>

#include <jni.h>
#include <rtmp.h>
#include <log.h>

#include "debug.h"
#include "logging.h"
#include "nalu.h"

void __cb_rtmp_log(int level, const char * fmt, va_list ap);

extern "C"
JNIEXPORT jstring JNICALL
Java_com_rex_qly_Rtmp_nativeVersion(JNIEnv* env, jclass clazz)
{
    LOGV("nativeVersion+");
    int major = (RTMP_LibVersion() & 0xFFFF0000) >> 16;
    int minor = (RTMP_LibVersion() & 0x0000FF00) >> 8;

    char version[100];
    sprintf(version, "%d.%d", major, minor);
    LOGV("nativeVersion- version:%s", version);
    return env->NewStringUTF(version);
}

extern "C"
JNIEXPORT long JNICALL
Java_com_rex_qly_Rtmp_nativeOpen(JNIEnv* env, jclass clazz, jstring jurl)
{
    const char * url = env->GetStringUTFChars(jurl, nullptr);
    LOGV("nativeOpen+ url:<%s>", url);

    RTMP_LogSetCallback(__cb_rtmp_log);
    RTMP_LogSetLevel(RTMP_LOGINFO);
    //RTMP_LogSetLevel(RTMP_LOGDEBUG);  // Print RTMPPacket_Dump()
    //RTMP_LogSetLevel(RTMP_LOGDEBUG2); // Print RTMP_SendPacket()
    //RTMP_LogSetLevel(RTMP_LOGALL);

    RTMP * rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    if (!RTMP_SetupURL(rtmp, (char *) url)) {
        LOGW("Failed to setup URL <%s>", url);
        RTMP_Free(rtmp);
        rtmp = nullptr;
        goto exit;
    }

    RTMP_EnableWrite(rtmp);
    if (!RTMP_Connect(rtmp, nullptr)) {
        LOGW("Failed to connect");
        RTMP_Free(rtmp);
        rtmp = nullptr;
        goto exit;
    }

    if (!RTMP_ConnectStream(rtmp, 0)) {
        LOGW("Failed to connect stream");
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = nullptr;
        goto exit;
    }

exit:
    if (jurl) env->ReleaseStringUTFChars(jurl, url);
    LOGV("nativeOpen- rtmp:%p", rtmp);
    return reinterpret_cast<intptr_t>(rtmp);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_rex_qly_Rtmp_nativeSendVideoConfig(JNIEnv * env, jclass clazz,
        jlong ptr, jobject jsps, jobject jpps)
{
    RTMP * rtmp = reinterpret_cast<RTMP*>(ptr);
    if (! rtmp) {
        LOGW("RTMP not connected");
        return 0;
    }

    uint8_t * sps = (uint8_t *) env->GetDirectBufferAddress(jsps);
    int64_t sps_size = env->GetDirectBufferCapacity(jsps);
    uint8_t * pps = (uint8_t *) env->GetDirectBufferAddress(jpps);
    int64_t pps_size = env->GetDirectBufferCapacity(jpps);
    LOGV("nativeSendVideoConfig+ rtmp:%p sps:%p size:%" PRId64 " pps:%p size:%" PRId64, rtmp, sps, sps_size, pps, pps_size);
    //dump_buffer(sps, (size_t) sps_size, "SPS");
    //dump_buffer(pps, (size_t) pps_size, "PPS");
    // SPS
    // 00 00 00 01 67 42 80 1f da 02 20 22 7b 96 52 0a
    // 0c 0c 0d a1 42 6a
    // PPS
    // 00 00 00 01 68 ce 06 e2
    if (0 == ::memcmp(sps, CSD, sizeof(CSD))) {
        sps += sizeof(CSD);
        sps_size -= sizeof(CSD);
    }
    if (0 == ::memcmp(pps, CSD, sizeof(CSD))) {
        pps += sizeof(CSD);
        pps_size -= sizeof(CSD);
    }

    RTMPPacket packet = { 0 };
    RTMPPacket_Alloc(&packet, (uint32_t) (sps_size + pps_size + 16));
    RTMPPacket_Reset(&packet);

    uint32_t i = 0;
    uint8_t * body = (uint8_t *) packet.m_body;

    body[i++] = 0x17;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    // AVCDecoderConfigurationRecord
    body[i++] = 0x01;   // configuration version
    body[i++] = sps[1]; // profile
    body[i++] = sps[2]; // profile compatibility
    body[i++] = sps[3]; // level
    body[i++] = 0xff;   // reserved

    // SPS
    body[i++] = 0xe1;   // reserved
    body[i++] = (uint8_t) ((sps_size >> 8) & 0xff);
    body[i++] = (uint8_t) ((sps_size)      & 0xff);
    memcpy(&body[i], sps, (size_t) sps_size);
    i += sps_size;

    // PPS
    body[i++] = 0x01;   // pps number
    body[i++] = (uint8_t) ((pps_size >> 8) & 0xff);
    body[i++] = (uint8_t) ((pps_size)      & 0xff);
    memcpy(&body[i], pps, (size_t) pps_size);
    i += pps_size;

    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_hasAbsTimestamp = 0;
    packet.m_nChannel = 0x06;
    packet.m_nTimeStamp = 0;
    packet.m_nInfoField2 = rtmp->m_stream_id;
    packet.m_nBodySize = i;
#if (LOG_LEVEL <= LOG_LEVEL_VERBOSE)
    RTMPPacket_Dump(&packet);
    //dump_buffer(body, packet.m_nBodySize, "Body");
#endif

    if (RTMP_IsConnected(rtmp)) {
        if (!RTMP_SendPacket(rtmp, &packet, true)) {
            LOGW("Failed to send packet");
        }
    } else {
        LOGW("RTMP not connected");
    }
    RTMPPacket_Free(&packet);
    LOGV("nativeSendVideoConfig-");
    return (jint) (sps_size + pps_size);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_rex_qly_Rtmp_nativeSendVideoData(JNIEnv * env, jclass clazz,
        jlong ptr, jobject jdata, jint offset, jint size, jlong pts)
{
    RTMP * rtmp = reinterpret_cast<RTMP*>(ptr);
    if (! rtmp) {
        LOGW("RTMP not connected");
        return 0;
    }

    uint8_t * data = (uint8_t *) env->GetDirectBufferAddress(jdata);
    LOGV("nativeSendVideoData+ rtmp:%p data:%p offset:%d size:%d pts:%" PRId64, rtmp, data, offset, size, pts);
    //(data + offset, (size_t) std::min(size, 128), "AnnexB");

    size_t pos   = (size_t) offset;
    size_t limit = (size_t) size;
    while (limit) {
        size_t len = nal_parse(data + pos, limit);
        if (len == 0) break;

        bool keyframe = false;
        int type = nal_type(data + pos, len);
        LOGV("nativeSendVideoData type:%d(%s) len:%zu", type, nal_type_str(type), len);
        if (type == H264_NAL_IDR_SLICE) {
            keyframe = true;
        }
        if (type == H264_NAL_SEI) { // Force skip the SEI
            data   = data + pos + len;
            pos    = 0;
            limit -= len;
            continue;
        }

        data[pos]     = (uint8_t) (((len - sizeof(CSD)) >> 24) & 0xff);
        data[pos + 1] = (uint8_t) (((len - sizeof(CSD)) >> 16) & 0xff);
        data[pos + 2] = (uint8_t) (((len - sizeof(CSD)) >>  8) & 0xff);
        data[pos + 3] = (uint8_t) (( len - sizeof(CSD))        & 0xff);

        {
            RTMPPacket packet = { 0 };
            RTMPPacket_Alloc(&packet, (uint32_t) (len + 5));
            RTMPPacket_Reset(&packet);

            uint32_t i = 0;
            uint8_t * body = (uint8_t *) packet.m_body;
            body[i++] = (uint8_t) (keyframe ? 0x17 : 0x27);
            body[i++] = 0x01;
            body[i++] = 0x00;
            body[i++] = 0x00;
            body[i++] = 0x00;
            memcpy(&body[i], data + pos, (size_t) len);
            i += len;

            packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
            packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
            packet.m_hasAbsTimestamp = 0;
            packet.m_nChannel = 0x06;
            packet.m_nTimeStamp = (uint32_t) pts;
            packet.m_nInfoField2 = rtmp->m_stream_id;
            packet.m_nBodySize = i;

#if (LOG_LEVEL <= LOG_LEVEL_VERBOSE)
            RTMPPacket_Dump(&packet);
            //dump_buffer(data + pos, std::min(len, (size_t) 128), "Avcc");
            //dump_buffer(body, std::min(packet.m_nBodySize, (uint32_t) 128), "Body");
#endif

            if (RTMP_IsConnected(rtmp)) {
                if (!RTMP_SendPacket(rtmp, &packet, true)) {
                    LOGW("Failed to send packet");
                }
            } else {
                LOGW("RTMP not connected");
            }

            RTMPPacket_Free(&packet);
        }

        pos   += len;
        limit -= len;
    }
    LOGV("nativeSendVideoData- size:%d", size);
    return size;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rex_qly_Rtmp_nativeClose(JNIEnv * env, jclass clazz, jlong ptr)
{
    RTMP * rtmp = reinterpret_cast<RTMP*>(ptr);
    LOGV("nativeRelease rtmp:%p", rtmp);
    if (rtmp) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
    }
}

void
__cb_rtmp_log(int level, const char * fmt, va_list ap)
{
    switch (level) {
    case RTMP_LOGALL:
    case RTMP_LOGDEBUG2:    LOG_VPRINT(LOG_LEVEL_VERBOSE, fmt, ap); break;
    case RTMP_LOGDEBUG:     LOG_VPRINT(LOG_LEVEL_DEBUG,   fmt, ap); break;
    case RTMP_LOGINFO:      LOG_VPRINT(LOG_LEVEL_INFO,    fmt, ap); break;
    case RTMP_LOGWARNING:   LOG_VPRINT(LOG_LEVEL_WARN,    fmt, ap); break;
    case RTMP_LOGERROR:     LOG_VPRINT(LOG_LEVEL_ERROR,   fmt, ap); break;
    default:                LOG_VPRINT(LOG_LEVEL_ERROR,   fmt, ap); break; // never reached
    }
}

// vim:ts=4:sw=4:et: