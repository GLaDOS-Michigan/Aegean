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

package Applications.jetty.websocket.common.message;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WriteCallback;
import Applications.jetty.websocket.api.extensions.OutgoingFrames;
import Applications.jetty.websocket.common.WebSocketSession;
import Applications.jetty.websocket.common.frames.BinaryFrame;
import Applications.jetty.websocket.common.io.FutureWriteCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

/**
 * Support for writing a single WebSocket BINARY message via a {@link OutputStream}
 */
public class MessageOutputStream extends OutputStream {
    private static final Logger LOG = Log.getLogger(MessageOutputStream.class);
    private final OutgoingFrames outgoing;
    private final ByteBufferPool bufferPool;
    private long frameCount = 0;
    private BinaryFrame frame;
    private ByteBuffer buffer;
    private FutureWriteCallback blocker;
    private WriteCallback callback;
    private boolean closed = false;

    public MessageOutputStream(OutgoingFrames outgoing, int bufferSize, ByteBufferPool bufferPool) {
        this.outgoing = outgoing;
        this.bufferPool = bufferPool;
        this.buffer = bufferPool.acquire(bufferSize, true);
        BufferUtil.flipToFill(buffer);
        this.frame = new BinaryFrame();
    }

    public MessageOutputStream(WebSocketSession session) {
        this(session.getOutgoingHandler(), session.getPolicy().getMaxBinaryMessageBufferSize(), session.getBufferPool());
    }

    private void assertNotClosed() throws IOException {
        if (closed) {
            IOException e = new IOException("Stream is closed");
            notifyFailure(e);
            throw e;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        assertNotClosed();
        LOG.debug("close()");

        // finish sending whatever in the buffer with FIN=true
        flush(true);

        // close stream
        LOG.debug("Sent Frame Count: {}", frameCount);
        closed = true;
        try {
            if (callback != null) {
                callback.writeSuccess();
            }
            super.close();
            bufferPool.release(buffer);
            LOG.debug("closed");
        } catch (IOException e) {
            notifyFailure(e);
            throw e;
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        LOG.debug("flush()");
        assertNotClosed();

        // flush whatever is in the buffer with FIN=false
        flush(false);
        try {
            super.flush();
            LOG.debug("flushed");
        } catch (IOException e) {
            notifyFailure(e);
            throw e;
        }
    }

    /**
     * Flush whatever is in the buffer.
     *
     * @param fin fin flag
     * @throws IOException
     */
    private synchronized void flush(boolean fin) throws IOException {
        BufferUtil.flipToFlush(buffer, 0);
        LOG.debug("flush({}): {}", fin, BufferUtil.toDetailString(buffer));
        frame.setPayload(buffer);
        frame.setFin(fin);

        try {
            blocker = new FutureWriteCallback();
            outgoing.outgoingFrame(frame, blocker);
            try {
                // block on write
                blocker.get();
                // block success
                frameCount++;
                frame.setIsContinuation();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    } else {
                        throw new IOException(cause);
                    }
                }
                throw new IOException("Failed to flush", e);
            } catch (InterruptedException e) {
                throw new IOException("Failed to flush", e);
            }
        } catch (IOException e) {
            notifyFailure(e);
            throw e;
        }
    }

    private void notifyFailure(IOException e) {
        if (callback != null) {
            callback.writeFailed(e);
        }
    }

    public void setCallback(WriteCallback callback) {
        this.callback = callback;
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        try {
            this.write(b, 0, b.length);
        } catch (IOException e) {
            notifyFailure(e);
            throw e;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        LOG.debug("write(byte[{}], {}, {})", b.length, off, len);
        int left = len; // bytes left to write
        int offset = off; // offset within provided array
        while (left > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("buffer: {}", BufferUtil.toDetailString(buffer));
            }
            int space = buffer.remaining();
            assert (space > 0);
            int size = Math.min(space, left);
            buffer.put(b, offset, size);
            assert (size > 0);
            left -= size; // decrement bytes left
            if (left > 0) {
                flush(false);
            }
            offset += size; // increment offset
        }
    }

    @Override
    public synchronized void write(int b) throws IOException {
        assertNotClosed();

        // buffer up to limit, flush once buffer reached.
        buffer.put((byte) b);
        if (buffer.remaining() <= 0) {
            flush(false);
        }
    }
}
