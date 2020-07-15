#ifndef _LOGGING_H_
#define _LOGGING_H_

#ifndef LOG_TAG
#  define LOG_TAG   "jni"
#endif

#ifndef LOG_LEVEL
#  define LOG_LEVEL LOG_LEVEL_DEBUG
#endif

// values match android_LogPriority in android/log.h
#define LOG_LEVEL_ALL       1
#define LOG_LEVEL_VERBOSE   2
#define LOG_LEVEL_DEBUG     3
#define LOG_LEVEL_INFO      4
#define LOG_LEVEL_WARN      5
#define LOG_LEVEL_ERROR     6
#define LOG_LEVEL_FATAL     7
#define LOG_LEVEL_SILENT    8

//#include <stdio.h>
//#include <stdarg.h>
#include <jni.h>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif

#define LOG_PRINT(prio, format, ...)  __android_log_print(prio, LOG_TAG, format, ##__VA_ARGS__)
#define LOG_VPRINT(prio, format, ap)  __android_log_vprint(prio, LOG_TAG, format, ap)
#define LOG_WRITE(prio, msg)          __android_log_write(prio, LOG_TAG, msg)
#define LOG_ASSERT(cond, format, ...) __android_log_assert(cond, LOG_TAG, format, ##__VA_ARGS__)

#ifdef __cplusplus
} //extern "C"
#endif

#if LOG_LEVEL > LOG_LEVEL_VERBOSE
#  define LOGV(...)
#else
#  define LOGV(...) LOG_PRINT(LOG_LEVEL_VERBOSE, __VA_ARGS__)
#endif

#if LOG_LEVEL > LOG_LEVEL_DEBUG
#  define LOGD(...)
#else
#  define LOGD(...) LOG_PRINT(LOG_LEVEL_DEBUG, __VA_ARGS__)
#endif

#if LOG_LEVEL > LOG_LEVEL_INFO
#  define LOGI(...)
#else
#  define LOGI(...) LOG_PRINT(LOG_LEVEL_INFO, __VA_ARGS__)
#endif

#if LOG_LEVEL > LOG_LEVEL_WARN
#  define LOGW(...)
#else
#  define LOGW(...) LOG_PRINT(LOG_LEVEL_WARN, __VA_ARGS__)
#endif

#if LOG_LEVEL > LOG_LEVEL_ERROR
#  define LOGE(...)
#else
#  define LOGE(...) LOG_PRINT(LOG_LEVEL_ERROR, __VA_ARGS__)
#endif

#endif /* #ifndef _LOGGING_H_ */

// vim:ts=4:sw=4:et:
