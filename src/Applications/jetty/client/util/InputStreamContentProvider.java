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

package Applications.jetty.client.util;

import Applications.jetty.client.api.ContentProvider;
import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A {@link ContentProvider} for an {@link InputStream}.
 * <p/>
 * The input stream is read once and therefore fully consumed.
 * Invocations to the {@link #iterator()} method after the first will return an "empty" iterator
 * because the stream has been consumed on the first invocation.
 * <p/>
 * However, it is possible for subclasses to override {@link #onRead(byte[], int, int)} to copy
 * the content read from the stream to another location (for example a file), and be able to
 * support multiple invocations of {@link #iterator()}, returning the iterator provided by this
 * class on the first invocation, and an iterator on the bytes copied to the other location
 * for subsequent invocations.
 * <p/>
 * It is possible to specify, at the constructor, a buffer size used to read content from the
 * stream, by default 4096 bytes.
 * <p/>
 * The {@link InputStream} passed to the constructor is by default closed when is it fully
 * consumed (or when an exception is thrown while reading it), unless otherwise specified
 * to the {@link #InputStreamContentProvider(java.io.InputStream, int, boolean) constructor}.
 */
public class InputStreamContentProvider implements ContentProvider {
    private static final Logger LOG = Log.getLogger(InputStreamContentProvider.class);

    private final InputStream stream;
    private final int bufferSize;
    private final boolean autoClose;

    public InputStreamContentProvider(InputStream stream) {
        this(stream, 4096);
    }

    public InputStreamContentProvider(InputStream stream, int bufferSize) {
        this(stream, bufferSize, true);
    }

    public InputStreamContentProvider(InputStream stream, int bufferSize, boolean autoClose) {
        this.stream = stream;
        this.bufferSize = bufferSize;
        this.autoClose = autoClose;
    }

    @Override
    public long getLength() {
        return -1;
    }

    /**
     * Callback method invoked just after having read from the stream,
     * but before returning the iteration element (a {@link ByteBuffer}
     * to the caller.
     * <p/>
     * Subclasses may override this method to copy the content read from
     * the stream to another location (a file, or in memory if the content
     * is known to fit).
     *
     * @param buffer the byte array containing the bytes read
     * @param offset the offset from where bytes should be read
     * @param length the length of the bytes read
     * @return a {@link ByteBuffer} wrapping the byte array
     */
    protected ByteBuffer onRead(byte[] buffer, int offset, int length) {
        if (length <= 0)
            return BufferUtil.EMPTY_BUFFER;
        return ByteBuffer.wrap(buffer, offset, length);
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
        return new InputStreamIterator();
    }

    /**
     * Iterating over an {@link InputStream} is tricky, because {@link #hasNext()} must return false
     * if the stream reads -1. However, we don't know what to return until we read the stream, which
     * means that stream reading must be performed by {@link #hasNext()}, which introduces a side-effect
     * on what is supposed to be a simple query method (with respect to the Query Command Separation
     * Principle).
     * <p/>
     * Alternatively, we could return {@code true} from {@link #hasNext()} even if we don't know that
     * we will read -1, but then when {@link #next()} reads -1 it must return an empty buffer.
     * However this is problematic, since GETs with no content indication would become GET with chunked
     * content, and not understood by servers.
     * <p/>
     * Therefore we need to make sure that {@link #hasNext()} does not perform any side effect (so that
     * it can be called multiple times) until {@link #next()} is called.
     */
    private class InputStreamIterator implements Iterator<ByteBuffer> {
        private Throwable failure;
        private ByteBuffer buffer;
        private Boolean hasNext;

        @Override
        public boolean hasNext() {
            try {
                if (hasNext != null)
                    return hasNext;

                byte[] bytes = new byte[bufferSize];
                int read = stream.read(bytes);
                LOG.debug("Read {} bytes from {}", read, stream);
                if (read > 0) {
                    buffer = onRead(bytes, 0, read);
                    hasNext = Boolean.TRUE;
                    return true;
                } else if (read < 0) {
                    hasNext = Boolean.FALSE;
                    close();
                    return false;
                } else {
                    buffer = BufferUtil.EMPTY_BUFFER;
                    hasNext = Boolean.TRUE;
                    return true;
                }
            } catch (Throwable x) {
                LOG.debug(x);
                if (failure == null) {
                    failure = x;
                    // Signal we have more content to cause a call to
                    // next() which will throw NoSuchElementException.
                    hasNext = Boolean.TRUE;
                    close();
                    return true;
                }
                throw new IllegalStateException();
            }
        }

        @Override
        public ByteBuffer next() {
            if (failure != null)
                throw (NoSuchElementException) new NoSuchElementException().initCause(failure);
            ByteBuffer result = buffer;
            if (result == null)
                throw new NoSuchElementException();
            buffer = null;
            hasNext = null;
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void close() {
            if (autoClose) {
                try {
                    stream.close();
                } catch (IOException x) {
                    LOG.ignore(x);
                }
            }
        }
    }
}
