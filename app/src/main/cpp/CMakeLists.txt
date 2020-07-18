
add_library(rtmp_jni SHARED
    debug.cpp
    nalu.cpp
    rtmp_jni.cpp
)
target_link_libraries(rtmp_jni PRIVATE
    librtmp log
)
