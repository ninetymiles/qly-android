#define LOG_TAG     "librtmpjni"
//#define LOG_LEVEL   LOG_LEVEL_ALL

#include <string>

#include <jni.h>
#include <rtmp.h>

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

// vim:ts=4:sw=4:et:
