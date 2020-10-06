
add_library(avcodec STATIC IMPORTED GLOBAL)
set_target_properties(avcodec PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavcodec.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

add_library(avformat STATIC IMPORTED GLOBAL)
set_target_properties(avformat PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavformat.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

add_library(avutil STATIC IMPORTED GLOBAL)
set_target_properties(avutil PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavutil.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

#add_library(swresample STATIC IMPORTED GLOBAL)
#set_target_properties(swresample PROPERTIES
#    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libswresample.a
#    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}include)

#add_library(swscale STATIC IMPORTED GLOBAL)
#set_target_properties(swscale PROPERTIES
#    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libswscale.a
#    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

