#define LOG_TAG     "librtmpjni"
//#define LOG_LEVEL   LOG_LEVEL_ALL

#include <stdio.h>
#include <stdint.h>
#include <string.h>

#include "debug.h"
#include "logging.h"
#include "nalu.h"

#define CMP_EQUAL   0

size_t
nal_parse(const uint8_t * data, size_t data_size)
{
    int pos_cur = ::memcmp(data, CSD, sizeof(CSD));
    if (pos_cur == CMP_EQUAL) {
        uint8_t * pos_next = (uint8_t *) ::memmem(data + sizeof(CSD), data_size - sizeof(CSD), CSD, sizeof(CSD));
        if (pos_next) {
            return pos_next - data;
        } else {
            return data_size;
        }
    }
    return 0;
}

int
nal_type(const uint8_t * data, size_t data_size)
{
    int pos_cur = ::memcmp(data, CSD, sizeof(CSD));
    if (pos_cur == CMP_EQUAL && data_size > sizeof(CSD)) {
        return (data[sizeof(CSD)] & 0x1F);
    }
    return 0;
}

// vim:ts=4:sw=4:et:
