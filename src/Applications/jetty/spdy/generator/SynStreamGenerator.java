//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.spdy.generator;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.spdy.SessionException;
import Applications.jetty.spdy.StreamException;
import Applications.jetty.spdy.api.SPDY;
import Applications.jetty.spdy.api.SessionStatus;
import Applications.jetty.spdy.api.StreamStatus;
import Applications.jetty.spdy.frames.ControlFrame;
import Applications.jetty.spdy.frames.SynStreamFrame;
import Applications.jetty.util.BufferUtil;

import java.nio.ByteBuffer;

public class SynStreamGenerator extends ControlFrameGenerator {
    private final HeadersBlockGenerator headersBlockGenerator;

    public SynStreamGenerator(ByteBufferPool bufferPool, HeadersBlockGenerator headersBlockGenerator) {
        super(bufferPool);
        this.headersBlockGenerator = headersBlockGenerator;
    }

    @Override
    public ByteBuffer generate(ControlFrame frame) {
        SynStreamFrame synStream = (SynStreamFrame) frame;
        short version = synStream.getVersion();

        ByteBuffer headersBuffer = headersBlockGenerator.generate(version, synStream.getHeaders());

        int frameBodyLength = 10;

        int frameLength = frameBodyLength + headersBuffer.remaining();
        if (frameLength > 0xFF_FF_FF) {
            // Too many headers, but unfortunately we have already modified the compression
            // context, so we have no other choice than tear down the connection.
            throw new SessionException(SessionStatus.PROTOCOL_ERROR, "Too many headers");
        }

        int totalLength = ControlFrame.HEADER_LENGTH + frameLength;

        ByteBuffer buffer = getByteBufferPool().acquire(totalLength, Generator.useDirectBuffers);
        BufferUtil.clearToFill(buffer);
        generateControlFrameHeader(synStream, frameLength, buffer);

        int streamId = synStream.getStreamId();
        buffer.putInt(streamId & 0x7F_FF_FF_FF);
        buffer.putInt(synStream.getAssociatedStreamId() & 0x7F_FF_FF_FF);
        writePriority(streamId, version, synStream.getPriority(), buffer);
        buffer.put((byte) synStream.getSlot());

        buffer.put(headersBuffer);

        buffer.flip();
        return buffer;
    }

    private void writePriority(int streamId, short version, byte priority, ByteBuffer buffer) {
        switch (version) {
            case SPDY.V2:
                priority <<= 6;
                break;
            case SPDY.V3:
                priority <<= 5;
                break;
            default:
                throw new StreamException(streamId, StreamStatus.UNSUPPORTED_VERSION);
        }
        buffer.put(priority);
    }
}
