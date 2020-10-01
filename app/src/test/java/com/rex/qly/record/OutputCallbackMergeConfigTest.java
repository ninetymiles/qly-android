package com.rex.qly.record;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OutputCallbackMergeConfigTest {

    @Test
    public void testDirectByteBuffer() {
        SurfaceRecorder.OutputCallback cb = mock(SurfaceRecorder.OutputCallback.class);
        OutputCallbackMergeConfig wrapper = new OutputCallbackMergeConfig(cb);

        // Verify onConfig() with direct buffer
        wrapper.onConfig(ByteBuffer.allocateDirect(16), ByteBuffer.allocateDirect(8));

        ArgumentCaptor<ByteBuffer> sps = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<ByteBuffer> pps = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb).onConfig(sps.capture(), pps.capture());
        assertTrue(sps.getValue().isDirect());
        assertTrue(pps.getValue().isDirect());

        // Verify onFrame() with direct buffer
        wrapper.onFrame(ByteBuffer.allocateDirect(24), 0, 24, 0);

        ArgumentCaptor<ByteBuffer> frame = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb).onFrame(frame.capture(), anyInt(), anyInt(), anyLong());
        assertTrue(frame.getValue().isDirect());
    }

    @Test
    public void testOnFrame() {
        byte[] sps = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x67, (byte) 0x42, (byte) 0x80, (byte) 0x1f,
                (byte) 0xda, (byte) 0x02, (byte) 0x20, (byte) 0x22, (byte) 0x7b, (byte) 0x96, (byte) 0x52, (byte) 0x0a,
        };
        byte[] pps = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x68, (byte) 0xce, (byte) 0x06, (byte) 0xe2,
        };
        byte[] sei = new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x05, (byte) 0x14, (byte) 0xe9,
                (byte) 0x58, (byte) 0xf4, (byte) 0x91, (byte) 0x7f, (byte) 0x6a, (byte) 0x42, (byte) 0xe7, (byte) 0x8e,
                (byte) 0x83, (byte) 0x08, (byte) 0xd5, (byte) 0x18, (byte) 0x95, (byte) 0xb2, (byte) 0xd5, (byte) 0x20,
                (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x80,
        };
        SurfaceRecorder.OutputCallback cb = mock(SurfaceRecorder.OutputCallback.class);
        OutputCallbackMergeConfig wrapper = new OutputCallbackMergeConfig(cb);
        wrapper.onConfig(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps));
        wrapper.onFrame(ByteBuffer.wrap(sei), 0, sei.length, 0);

        ArgumentCaptor<ByteBuffer> buffer = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb).onFrame(buffer.capture(), eq(0), eq(sps.length + pps.length + sei.length), eq(0L));

        byte[] data = new byte[sps.length + pps.length + sei.length];
        buffer.getValue().get(data);

        byte[] expected = ByteBuffer.allocate(sps.length + pps.length + sei.length)
                .put(sps)
                .put(pps)
                .put(sei)
                .array();
        assertArrayEquals(expected, data);
    }
}