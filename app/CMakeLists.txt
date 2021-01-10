cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_STANDARD 14)

if (ANDROID)
    add_definitions(-DLOG_OUTPUT_LOGCAT)
else ()
    add_definitions(-DLOG_OUTPUT_STDOUT)
endif ()

add_subdirectory(src/main/cpp)
add_subdirectory(cmake/external/libffmpeg)
add_subdirectory(cmake/external/librtmp)
add_subdirectory(cmake/external/gtest)
