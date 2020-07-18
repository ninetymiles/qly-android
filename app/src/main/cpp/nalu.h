#ifndef _NALU_H_
#define _NALU_H_

#include <stdint.h>

// Export from ffmpeg/libavcodec/h264.h
enum {
    H264_NAL_SLICE           = 1,
    H264_NAL_DPA             = 2,
    H264_NAL_DPB             = 3,
    H264_NAL_DPC             = 4,
    H264_NAL_IDR_SLICE       = 5,
    H264_NAL_SEI             = 6,
    H264_NAL_SPS             = 7,
    H264_NAL_PPS             = 8,
    H264_NAL_AUD             = 9,
    H264_NAL_END_SEQUENCE    = 10,
    H264_NAL_END_STREAM      = 11,
    H264_NAL_FILLER_DATA     = 12,
    H264_NAL_SPS_EXT         = 13,
    H264_NAL_AUXILIARY_SLICE = 19,
};

size_t nal_parse(const uint8_t * data, size_t data_size);
int nal_type(const uint8_t * data, size_t data_size);

#endif // _NALU_H_

// vim:ts=4:sw=4:et:
