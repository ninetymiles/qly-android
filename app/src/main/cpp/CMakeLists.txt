
add_library(rtmp_jni SHARED
    debug.cpp
    nalu.cpp
    rtmp_jni.cpp
)
target_link_libraries(rtmp_jni
    librtmp log
)

# BEGIN GoogleTest
add_executable(rtmp_test
    nalu.test.cpp
)
target_link_libraries(rtmp_test
    rtmp_jni
    gmock_main
)
add_test(NAME gtest COMMAND rtmp_test)
# END GoogleTest
