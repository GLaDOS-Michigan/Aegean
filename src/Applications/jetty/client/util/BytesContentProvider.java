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

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A {@link ContentProvider} for byte arrays.
 */
public class BytesContentProvider implements ContentProvider {
    private final byte[][] bytes;
    private final long length;

    public BytesContentProvider(byte[]... bytes) {
        this.bytes = bytes;
        long length = 0;
        for (byte[] buffer : bytes)
            length += buffer.length;
        this.length = length;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
        return new Iterator<ByteBuffer>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < bytes.length;
            }

            @Override
            public ByteBuffer next() {
                try {
                    return ByteBuffer.wrap(bytes[index++]);
                } catch (ArrayIndexOutOfBoundsException x) {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
