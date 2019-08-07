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

package Applications.jetty.spdy.parser;

import Applications.jetty.spdy.CompressionFactory;
import Applications.jetty.spdy.api.ReplyInfo;
import Applications.jetty.spdy.api.SPDY;
import Applications.jetty.spdy.frames.ControlFrameType;
import Applications.jetty.spdy.frames.SynReplyFrame;
import Applications.jetty.util.Fields;

import java.nio.ByteBuffer;

public class SynReplyBodyParser extends ControlFrameBodyParser {
    private final Fields headers = new Fields();
    private final ControlFrameParser controlFrameParser;
    private final HeadersBlockParser headersBlockParser;
    private State state = State.STREAM_ID;
    private int cursor;
    private int streamId;

    public SynReplyBodyParser(CompressionFactory.Decompressor decompressor, ControlFrameParser controlFrameParser) {
        this.controlFrameParser = controlFrameParser;
        this.headersBlockParser = new SynReplyHeadersBlockParser(decompressor);
    }

    @Override
    public boolean parse(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            switch (state) {
                case STREAM_ID: {
                    if (buffer.remaining() >= 4) {
                        streamId = buffer.getInt() & 0x7F_FF_FF_FF;
                        state = State.ADDITIONAL;
                    } else {
                        state = State.STREAM_ID_BYTES;
                        cursor = 4;
                    }
                    break;
                }
                case STREAM_ID_BYTES: {
                    byte currByte = buffer.get();
                    --cursor;
                    streamId += (currByte & 0xFF) << 8 * cursor;
                    if (cursor == 0) {
                        streamId &= 0x7F_FF_FF_FF;
                        state = State.ADDITIONAL;
                    }
                    break;
                }
                case ADDITIONAL: {
                    switch (controlFrameParser.getVersion()) {
                        case SPDY.V2: {
                            if (buffer.remaining() >= 2) {
                                buffer.getShort();
                                state = State.HEADERS;
                            } else {
                                state = State.ADDITIONAL_BYTES;
                                cursor = 2;
                            }
                            break;
                        }
                        case SPDY.V3: {
                            state = State.HEADERS;
                            break;
                        }
                        default: {
                            throw new IllegalStateException();
                        }
                    }
                    break;
                }
                case ADDITIONAL_BYTES: {
                    assert controlFrameParser.getVersion() == SPDY.V2;
                    buffer.get();
                    --cursor;
                    if (cursor == 0)
                        state = State.HEADERS;
                    break;
                }
                case HEADERS: {
                    short version = controlFrameParser.getVersion();
                    int length = controlFrameParser.getLength() - getSynReplyDataLength(version);
                    if (headersBlockParser.parse(streamId, version, length, buffer)) {
                        byte flags = controlFrameParser.getFlags();
                        if (flags != 0 && flags != ReplyInfo.FLAG_CLOSE)
                            throw new IllegalArgumentException("Invalid flag " + flags + " for frame " + ControlFrameType.SYN_REPLY);

                        SynReplyFrame frame = new SynReplyFrame(version, flags, streamId, new Fields(headers, true));
                        controlFrameParser.onControlFrame(frame);

                        reset();
                        return true;
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException();
                }
            }
        }
        return false;
    }

    private int getSynReplyDataLength(short version) {
        switch (version) {
            case 2:
                return 6;
            case 3:
                return 4;
            default:
                throw new IllegalStateException();
        }
    }

    private void reset() {
        headers.clear();
        state = State.STREAM_ID;
        cursor = 0;
        streamId = 0;
    }

    private enum State {
        STREAM_ID, STREAM_ID_BYTES, ADDITIONAL, ADDITIONAL_BYTES, HEADERS
    }

    private class SynReplyHeadersBlockParser extends HeadersBlockParser {
        public SynReplyHeadersBlockParser(CompressionFactory.Decompressor decompressor) {
            super(decompressor);
        }

        @Override
        protected void onHeader(String name, String[] values) {
            for (String value : values)
                headers.add(name, value);
        }
    }
}
