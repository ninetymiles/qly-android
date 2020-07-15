#define LOG_TAG     "librtmpjni"
//#define LOG_LEVEL   LOG_LEVEL_ALL

#include <algorithm>
#include <cinttypes>
#include <cstring>

#include <jni.h>
#include <rtmp.h>

#include "debug.h"
#include "logging.h"

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
    uint8_t * sps = (uint8_t *) env->GetDirectBufferAddress(jsps);
    int64_t sps_size = env->GetDirectBufferCapacity(jsps);
    uint8_t * pps = (uint8_t *) env->GetDirectBufferAddress(jpps);
    int64_t pps_size = env->GetDirectBufferCapacity(jpps);
    LOGV("nativeSendVideoConfig+ rtmp:%p sps:%p size:%" PRId64 " pps:%p size:%" PRId64, rtmp, sps, sps_size, pps, pps_size);
    dump_buffer(sps, (size_t) sps_size, "SPS");
    dump_buffer(pps, (size_t) pps_size, "PPS");

    if (sps[0] == 0x00 && sps[1] == 0x00 && sps[2] == 0x00 && sps[3] == 0x01) {
        sps += 4;
        sps_size -= 4;
        dump_buffer(sps, (size_t) sps_size, "SPS");
    }
    if (pps[0] == 0x00 && pps[1] == 0x00 && pps[2] == 0x00 && pps[3] == 0x01) {
        pps += 4;
        pps_size -= 4;
        dump_buffer(pps, (size_t) pps_size, "PPS");
    }

    RTMPPacket packet = { 0 };
    RTMPPacket_Alloc(&packet, (int) (sps_size + pps_size));

    uint32_t i = 0;
    uint8_t * body = (uint8_t *) packet.m_body;

    body[i++] = 0x17;
    body[i++] = 0x00;

    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    // AVCDecoderConfigurationRecord
    body[i++] = 0x01;
    body[i++] = sps[1];
    body[i++] = sps[2];
    body[i++] = sps[3];
    body[i++] = 0xff;

    // SPS
    body[i++] = 0xe1;
    body[i++] = (unsigned char) ((sps_size >> 8) & 0xff);
    body[i++] = (unsigned char) (sps_size & 0xff);
    memcpy(&body[i], sps, (size_t) sps_size);
    i +=  sps_size;

    // PPS
    body[i++] = 0x01;
    body[i++] = (unsigned char) ((pps_size >> 8) & 0xff);
    body[i++] = (unsigned char) (pps_size & 0xff);
    memcpy(&body[i], pps, (size_t) pps_size);
    i +=  pps_size;

    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nBodySize = i;
    packet.m_nChannel = 0x04;
    packet.m_nTimeStamp = 0;
    packet.m_hasAbsTimestamp = 0;
    packet.m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet.m_nInfoField2 = rtmp->m_stream_id;

    int result = 0;
    if (RTMP_IsConnected(rtmp)) {
        result = RTMP_SendPacket(rtmp, &packet, true);
    }
    LOGV("nativeSendVideoConfig- result:%d", result);
    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_rex_qly_Rtmp_nativeSendVideoData(JNIEnv * env, jclass clazz,
        jlong ptr, jobject jdata, jint offset, jint size, jlong pts)
{
    RTMP * rtmp = reinterpret_cast<RTMP*>(ptr);
    uint8_t * data = (uint8_t *) env->GetDirectBufferAddress(jdata);
    LOGV("nativeSendVideoData+ rtmp:%p data:%p offset:%d size:%d pts:%" PRId64, rtmp, data, offset, size, pts);
    dump_buffer(data + offset, (size_t) std::min(size, 64), "Data");

    if (data[offset] == 0x00 && data[offset + 1] == 0x00 && data[offset + 2] == 0x00 && data[offset + 3] == 0x01) {
        offset += 4;
        size -= 4;
        dump_buffer(data + offset, (size_t) std::min(size, 64), "Data");
    }
    data += offset;

    RTMPPacket packet = { 0 };
    RTMPPacket_Alloc(&packet, size + 9);

    uint32_t i = 0;
    uint8_t * body = (uint8_t *) packet.m_body;

    if ((data[0] & 0x1f) == 0x05) { // NAL_SLICE_IDR
        body[i++] = 0x17;
    } else {
        body[i++] = 0x27;
    }

    // Nal unit
    body[i++] = 0x01;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    body[i++] = (unsigned char) ((size >> 24) & 0xff);
    body[i++] = (unsigned char) ((size >> 16) & 0xff);
    body[i++] = (unsigned char) ((size >>  8) & 0xff);
    body[i++] = (unsigned char) ((size ) & 0xff);

    memcpy(&body[i], data, (size_t) size);

    packet.m_hasAbsTimestamp = 0;
    packet.m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet.m_nInfoField2 = rtmp->m_stream_id;
    packet.m_nChannel = 0x04;
    packet.m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet.m_nTimeStamp = (uint32_t) pts;
    packet.m_nBodySize = (uint32_t) size + i;

    int result = 0;
    if (RTMP_IsConnected(rtmp)) {
        result = RTMP_SendPacket(rtmp, &packet, true);
    }
    LOGV("nativeSendVideoData- result:%d", result);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rex_qly_Rtmp_nativeClose(JNIEnv * env, jclass clazz, jlong ptr)
{
    RTMP * rtmp = reinterpret_cast<RTMP*>(ptr);
    LOGV("nativeRelease rtmp:%p", rtmp);
    RTMP_Close(rtmp);
    RTMP_Free(rtmp);
}

// vim:ts=4:sw=4:et:
