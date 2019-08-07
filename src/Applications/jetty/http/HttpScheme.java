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

/* ------------------------------------------------------------------------------- */

/**
 */
public enum HttpScheme {
    HTTP("http"),
    HTTPS("https"),
    WS("ws"),
    WSS("wss");

    /* ------------------------------------------------------------ */
    public final static Trie<HttpScheme> CACHE = new ArrayTrie<HttpScheme>();

    static {
        for (HttpScheme version : HttpScheme.values())
            CACHE.put(version.asString(), version);
    }

    private final String _string;
    private final ByteBuffer _buffer;

    /* ------------------------------------------------------------ */
    HttpScheme(String s) {
        _string = s;
        _buffer = BufferUtil.toBuffer(s);
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer asByteBuffer() {
        return _buffer.asReadOnlyBuffer();
    }

    /* ------------------------------------------------------------ */
    public boolean is(String s) {
        return _string.equalsIgnoreCase(s);
    }

    public String asString() {
        return _string;
    }

    /* ------------------------------------------------------------ */
    @Override
    public String toString() {
        return _string;
    }

}
