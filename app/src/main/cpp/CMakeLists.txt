
add_library(rtmp_jni SHARED
    rtmp_jni.cpp
    debug.cpp
)
target_link_libraries(rtmp_jni PRIVATE
    librtmp log
)
