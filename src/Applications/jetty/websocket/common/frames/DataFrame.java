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

package Applications.jetty.websocket.common.frames;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.OpCode;
import Applications.jetty.websocket.common.WebSocketFrame;

/**
 * A Data Frame
 */
public class DataFrame extends WebSocketFrame {
    private boolean isPooledBuffer = false;

    protected DataFrame(byte opcode) {
        super(opcode);
    }

    /**
     * Construct new DataFrame based on headers of provided frame.
     * <p>
     * Useful for when working in extensions and a new frame needs to be created.
     */
    public DataFrame(Frame basedOn) {
        this(basedOn, false);
    }

    /**
     * Construct new DataFrame based on headers of provided frame, overriding for continuations if needed.
     * <p>
     * Useful for when working in extensions and a new frame needs to be created.
     */
    public DataFrame(Frame basedOn, boolean continuation) {
        super(basedOn.getOpCode());
        copyHeaders(basedOn);
        if (continuation) {
            setOpCode(OpCode.CONTINUATION);
        }
    }

    @Override
    public void assertValid() {
        /* no extra validation for data frames (yet) here */
    }

    @Override
    public boolean isControlFrame() {
        return false;
    }

    @Override
    public boolean isDataFrame() {
        return true;
    }

    /**
     * @return true if payload buffer is from a {@link ByteBufferPool} and can be released when appropriate to do so
     */
    public boolean isPooledBuffer() {
        return isPooledBuffer;
    }

    /**
     * Set the data frame to continuation mode
     */
    public void setIsContinuation() {
        setOpCode(OpCode.CONTINUATION);
    }

    /**
     * Sets a flag indicating that the underlying payload is from a {@link ByteBufferPool} and can be released when appropriate to do so
     */
    public void setPooledBuffer(boolean isPooledBuffer) {
        this.isPooledBuffer = isPooledBuffer;
    }
}
