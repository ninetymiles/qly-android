#define LOG_TAG "librtmpjni"

#include <stdio.h>
#include <stdint.h>
#include <string.h>

#include "logging.h"

#include "debug.h"

void
dump_buffer(const void * _data, size_t size, const char * title)
{
    const uint8_t * data = (uint8_t *) _data;
    if (title) {
        LOGD("dump_buffer <%s> %p size:%zu", title, data, size);
    } else {
        LOGD("dump_buffer %p size:%zu", data, size);
    }

    size_t i = 0;
    char buffer[3 * 16 + 1];
    for (i = 0; i < size; i++) {
        if (i % 16 == 0) memset(buffer, 0, sizeof(buffer));
        sprintf(buffer + ((i % 16) * 3), "%02x ", data[i]);
        if ((i + 1) % 16 == 0) LOGD("%s", buffer);
    }
    if (i % 16 != 0) LOGD("%s", buffer); // The rest part
}

// vim:ts=4:sw=4:et:
