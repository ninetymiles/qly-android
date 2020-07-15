package com.rex.qly.utils;

import java.nio.ByteBuffer;
import java.util.Locale;

public class Debug {

    public static String dumpByteBuffer(ByteBuffer buffer, int offset, int size) {
        StringBuffer hexString = new StringBuffer();
        int limit = offset + size;
        for (int i = offset; i < limit; i++) {
            hexString.append(String.format(Locale.US, "%02x ", buffer.get(i)));
        }
        buffer.rewind();
        return hexString.toString().trim();
    }
}
