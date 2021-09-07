
add_library(libavcodec STATIC IMPORTED GLOBAL)
set_target_properties(libavcodec PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavcodec.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

add_library(libavformat STATIC IMPORTED GLOBAL)
set_target_properties(libavformat PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavformat.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

add_library(libavutil STATIC IMPORTED GLOBAL)
set_target_properties(libavutil PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libavutil.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include
    INTERFACE_LINK_LIBRARIES z)

#add_library(swresample STATIC IMPORTED GLOBAL)
#set_target_properties(swresample PROPERTIES
#    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libswresample.a
#    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}include)

#add_library(swscale STATIC IMPORTED GLOBAL)
#set_target_properties(swscale PROPERTIES
#    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libswscale.a
#    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)

