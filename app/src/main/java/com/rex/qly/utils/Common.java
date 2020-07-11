/*
 * Copyright (C) 2007 - 2015 Splashtop Inc.
 *
 * Mirroring360
 */
package com.rex.qly.utils;

import java.nio.ByteBuffer;
import java.util.Locale;

public class Common {

    public static String dumpByteBuffer(ByteBuffer buffer) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < Math.min(buffer.limit(), 16); i++) {
            hexString.append(String.format(Locale.US, "%02x", buffer.get(i)));
        }
        return hexString.toString().trim();
    }

    public static String dumpByteArray(byte[] buffer, int offset, int size) {
        int length = Math.min(size, buffer.length);
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < length; i++) {
            hexString.append(String.format(Locale.US, "%02x", buffer[offset + i]));
            if ((i + 1) % 16 == 0) {
                hexString.append("\n");
            } else {
                hexString.append(" ");
            }
        }
        return hexString.toString().trim();
    }

    // Considering the covert performance, we using HEX pattern array to replace of "String.format"
    public static String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) return "";
        StringBuffer result = new StringBuffer(2 * bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            appendHex(result, bytes[i]);
        }

        return result.toString();
    }

    private static final  String HEX = "0123456789ABCDEF";

    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
    }
}
