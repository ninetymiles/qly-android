#ifndef _NALU_H_
#define _NALU_H_

#include <stdint.h>

static const char CSD[] = { 0x00, 0x00, 0x00, 0x01 };

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

static const char *
nal_type_str(int type)
{
    switch (type) {
    case H264_NAL_SLICE:            return "H264_NAL_SLICE";
    case H264_NAL_DPA:              return "H264_NAL_DPA";
    case H264_NAL_DPB:              return "H264_NAL_DPB";
    case H264_NAL_DPC:              return "H264_NAL_DPC";
    case H264_NAL_IDR_SLICE:        return "H264_NAL_IDR_SLICE";
    case H264_NAL_SEI:              return "H264_NAL_SEI";
    case H264_NAL_SPS:              return "H264_NAL_SPS";
    case H264_NAL_PPS:              return "H264_NAL_PPS";
    case H264_NAL_AUD:              return "H264_NAL_AUD";
    case H264_NAL_END_SEQUENCE:     return "H264_NAL_END_SEQUENCE";
    case H264_NAL_END_STREAM:       return "H264_NAL_END_STREAM";
    case H264_NAL_FILLER_DATA:      return "H264_NAL_FILLER_DATA";
    case H264_NAL_SPS_EXT:          return "H264_NAL_SPS_EXT";
    case H264_NAL_AUXILIARY_SLICE:  return "H264_NAL_AUXILIARY_SLICE";
    default:
        return "H264_NAL_UNKNOWN";
    }
}

size_t nal_parse(const uint8_t * data, size_t data_size);
int nal_type(const uint8_t * data, size_t data_size);

#endif // _NALU_H_

// vim:ts=4:sw=4:et:
