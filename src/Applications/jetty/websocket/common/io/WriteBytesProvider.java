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

package Applications.jetty.websocket.common.io;

import Applications.jetty.io.AbstractConnection;
import Applications.jetty.io.EndPoint;
import Applications.jetty.util.Callback;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.Generator;
import Applications.jetty.websocket.common.OpCode;
import Applications.jetty.websocket.common.frames.DataFrame;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for working with bytes destined for {@link EndPoint#write(Callback, ByteBuffer...)}
 */
public class WriteBytesProvider implements Callback {
    private class FrameEntry {
        protected final AtomicBoolean failed = new AtomicBoolean(false);
        protected final Frame frame;
        protected final Callback callback;
        /**
         * holds reference to header ByteBuffer, as it needs to be released on success/failure
         */
        private ByteBuffer headerBuffer;

        public FrameEntry(Frame frame, Callback callback) {
            this.frame = frame;
            this.callback = callback;
        }

        public ByteBuffer getHeaderBytes() {
            ByteBuffer buf = generator.generateHeaderBytes(frame);
            headerBuffer = buf;
            return buf;
        }

        public ByteBuffer getPayloadWindow() {
            // There is no need to release this ByteBuffer, as it is just a slice of the user provided payload
            return generator.getPayloadWindow(bufferSize, frame);
        }

        public void notifyFailure(Throwable t) {
            freeBuffers();
            if (failed.getAndSet(true) == false) {
                notifySafeFailure(callback, t);
            }
        }

        public void notifySucceeded() {
            freeBuffers();
            if (callback == null) {
                return;
            }
            try {
                callback.succeeded();
            } catch (Throwable t) {
                LOG.debug(t);
            }
        }

        public void freeBuffers() {
            if (headerBuffer != null) {
                generator.getBufferPool().release(headerBuffer);
                headerBuffer = null;
            }
            releasePayloadBuffer(frame);
        }

        /**
         * Indicate that the frame entry is done generating
         */
        public boolean isDone() {
            return frame.remaining() <= 0;
        }
    }

    private static final Logger LOG = Log.getLogger(WriteBytesProvider.class);

    /**
     * The websocket generator
     */
    private final Generator generator;
    /**
     * Flush callback, for notifying when a flush should be performed
     */
    private final Callback flushCallback;
    /**
     * Backlog of frames
     */
    private LinkedList<FrameEntry> queue;
    /**
     * the buffer input size
     */
    private int bufferSize = 2048;
    /**
     * the gathered write bytebuffer array limit
     */
    private int gatheredBufferLimit = 10;
    /**
     * Past Frames, not yet notified (from gathered generation/write)
     */
    private LinkedList<FrameEntry> past;
    /**
     * Currently active frame
     */
    private FrameEntry active;
    /**
     * Tracking for failure
     */
    private Throwable failure;
    /**
     * Is WriteBytesProvider closed to more WriteBytes being enqueued?
     */
    private AtomicBoolean closed;

    /**
     * Create a WriteBytesProvider with specified Generator and "flush" Callback.
     *
     * @param generator     the generator to use for converting {@link Frame} objects to network {@link ByteBuffer}s
     * @param flushCallback the flush callback to call, on a write event, after the write event has been processed by this {@link WriteBytesProvider}.
     *                      <p>
     *                      Used to trigger another flush of the next set of bytes.
     */
    public WriteBytesProvider(Generator generator, Callback flushCallback) {
        this.generator = Objects.requireNonNull(generator);
        this.flushCallback = Objects.requireNonNull(flushCallback);
        this.queue = new LinkedList<>();
        this.past = new LinkedList<>();
        this.closed = new AtomicBoolean(false);
    }

    /**
     * Force closure of write bytes
     */
    public void close() {
        LOG.debug(".close()");
        // Set queue closed, no new enqueue allowed.
        this.closed.set(true);
        // flush out backlog in queue
        failAll(new EOFException("Connection has been disconnected"));
    }

    public void enqueue(Frame frame, Callback callback) {
        Objects.requireNonNull(frame);
        LOG.debug("enqueue({}, {})", frame, callback);
        synchronized (this) {
            if (closed.get()) {
                // Closed for more frames.
                LOG.debug("Write is closed: {} {}", frame, callback);
                if (callback != null) {
                    callback.failed(new IOException("Write is closed"));
                }
                return;
            }

            if (failure != null) {
                // no changes when failed
                LOG.debug("Write is in failure: {} {}", frame, callback);
                notifySafeFailure(callback, failure);
                return;
            }

            FrameEntry entry = new FrameEntry(frame, callback);

            switch (frame.getOpCode()) {
                case OpCode.PING:
                    queue.addFirst(entry);
                    break;
                case OpCode.CLOSE:
                    closed.set(true);
                    // drop the rest of the queue?
                    queue.addLast(entry);
                    break;
                default:
                    queue.addLast(entry);
            }
        }
    }

    public void failAll(Throwable t) {
        // Collect entries for callback
        List<FrameEntry> callbacks = new ArrayList<>();

        synchronized (this) {
            // fail active (if set)
            if (active != null) {
                FrameEntry entry = active;
                active = null;
                callbacks.add(entry);
            }

            callbacks.addAll(past);
            callbacks.addAll(queue);

            past.clear();
            queue.clear();
        }

        // notify flush callback
        if (!callbacks.isEmpty()) {
            // TODO: always notify instead?
            flushCallback.failed(t);

            // notify entry callbacks
            for (FrameEntry entry : callbacks) {
                entry.notifyFailure(t);
            }
        }
    }

    /**
     * Callback failure.
     * <p>
     * Conditions: for Endpoint.write() failure.
     *
     * @param cause the cause of the failure
     */
    @Override
    public void failed(Throwable cause) {
        failAll(cause);
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Get the next set of ByteBuffers to write.
     *
     * @return the next set of ByteBuffers to write
     */
    public List<ByteBuffer> getByteBuffers() {
        List<ByteBuffer> bufs = null;
        int count = 0;
        synchronized (this) {
            for (; count < gatheredBufferLimit; count++) {
                if (active == null) {
                    if (queue.isEmpty()) {
                        // nothing in queue
                        return bufs;
                    }

                    // get current topmost entry
                    active = queue.pop();

                    // generate header
                    if (bufs == null) {
                        bufs = new ArrayList<>();
                    }
                    bufs.add(active.getHeaderBytes());
                    count++;
                }

                // collect payload window
                if (bufs == null) {
                    bufs = new ArrayList<>();
                }
                bufs.add(active.getPayloadWindow());
                if (active.isDone()) {
                    past.add(active);
                    active = null;
                }
            }
        }

        LOG.debug("Collected {} ByteBuffers", bufs.size());
        return bufs;
    }

    /**
     * Used to test for the final frame possible to be enqueued, the CLOSE frame.
     *
     * @return true if close frame has been enqueued already.
     */
    public boolean isClosed() {
        synchronized (this) {
            return closed.get();
        }
    }

    private void notifySafeFailure(Callback callback, Throwable t) {
        if (callback == null) {
            return;
        }
        try {
            callback.failed(t);
        } catch (Throwable e) {
            LOG.warn("Uncaught exception", e);
        }
    }

    public void releasePayloadBuffer(Frame frame) {
        if (!frame.hasPayload()) {
            return;
        }

        if (frame instanceof DataFrame) {
            DataFrame data = (DataFrame) frame;
            if (data.isPooledBuffer()) {
                ByteBuffer payload = frame.getPayload();
                generator.getBufferPool().release(payload);
            }
        }
    }

    /**
     * Set the buffer size used for generating ByteBuffers from the frames.
     * <p>
     * Value usually obtained from {@link AbstractConnection#getInputBufferSize()}
     *
     * @param bufferSize the buffer size to use
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Write of ByteBuffer succeeded.
     */
    @Override
    public void succeeded() {
        // Collect entries for callback
        List<FrameEntry> callbacks = new ArrayList<>();

        synchronized (this) {
            if ((active != null) && (active.frame.remaining() <= 0)) {
                // All done with active FrameEntry
                FrameEntry entry = active;
                active = null;
                callbacks.add(entry);
            }

            callbacks.addAll(past);
            past.clear();
        }

        // notify flush callback
        flushCallback.succeeded();

        // notify entry callbacks outside of synchronize
        for (FrameEntry entry : callbacks) {
            entry.notifySucceeded();
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("WriteBytesProvider[");
        b.append("flushCallback=").append(flushCallback);
        if (failure != null) {
            b.append(",failure=").append(failure.getClass().getName());
            b.append(":").append(failure.getMessage());
        } else {
            b.append(",active=").append(active);
            b.append(",queue.size=").append(queue.size());
            b.append(",past.size=").append(past.size());
        }
        b.append(']');
        return b.toString();
    }
}
