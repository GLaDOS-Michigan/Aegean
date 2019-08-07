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

package Applications.jetty.http;

import Applications.jetty.util.ArrayTrie;
import Applications.jetty.util.BufferUtil;
import Applications.jetty.util.Trie;

import java.nio.ByteBuffer;
import java.util.EnumSet;


/**
 *
 */
public enum HttpHeaderValue {
    CLOSE("close"),
    CHUNKED("chunked"),
    GZIP("gzip"),
    IDENTITY("identity"),
    KEEP_ALIVE("keep-alive"),
    CONTINUE("100-continue"),
    PROCESSING("102-processing"),
    TE("TE"),
    BYTES("bytes"),
    NO_CACHE("no-cache"),
    UPGRADE("Upgrade"),
    UNKNOWN("::UNKNOWN::");

    /* ------------------------------------------------------------ */
    public final static Trie<HttpHeaderValue> CACHE = new ArrayTrie<HttpHeaderValue>();

    static {
        for (HttpHeaderValue value : HttpHeaderValue.values())
            if (value != UNKNOWN)
                CACHE.put(value.toString(), value);
    }

    private final String _string;
    private final ByteBuffer _buffer;

    /* ------------------------------------------------------------ */
    HttpHeaderValue(String s) {
        _string = s;
        _buffer = BufferUtil.toBuffer(s);
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer toBuffer() {
        return _buffer.asReadOnlyBuffer();
    }

    /* ------------------------------------------------------------ */
    public String asString() {
        return _string;
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString() {
        return _string;
    }

    /* ------------------------------------------------------------ */
    private static EnumSet<HttpHeader> __known =
            EnumSet.of(HttpHeader.CONNECTION,
                    HttpHeader.TRANSFER_ENCODING,
                    HttpHeader.CONTENT_ENCODING);

    /* ------------------------------------------------------------ */
    public static boolean hasKnownValues(HttpHeader header) {
        if (header == null)
            return false;
        return __known.contains(header);
    }
}
