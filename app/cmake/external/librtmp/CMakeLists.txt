
set(LIBRTMP_SRC_DIR "rtmpdump-2.3/librtmp")

# TODO: Link with OpenSSL to enable crypto supports
add_definitions("-DNO_CRYPTO")
add_library(librtmp STATIC
    ${LIBRTMP_SRC_DIR}/amf.c
    ${LIBRTMP_SRC_DIR}/hashswf.c
    ${LIBRTMP_SRC_DIR}/log.c
    ${LIBRTMP_SRC_DIR}/parseurl.c
    ${LIBRTMP_SRC_DIR}/rtmp.c
    )
set_target_properties(librtmp PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/${LIBRTMP_SRC_DIR})
