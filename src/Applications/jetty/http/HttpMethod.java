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
import Applications.jetty.util.StringUtil;
import Applications.jetty.util.Trie;

import java.nio.ByteBuffer;


/* ------------------------------------------------------------------------------- */

/**
 */
public enum HttpMethod {
    GET,
    POST,
    HEAD,
    PUT,
    OPTIONS,
    DELETE,
    TRACE,
    CONNECT,
    MOVE;

    /* ------------------------------------------------------------ */

    /**
     * Optimised lookup to find a method name and trailing space in a byte array.
     *
     * @param bytes    Array containing ISO-8859-1 characters
     * @param position The first valid index
     * @param limit    The first non valid index
     * @return A HttpMethod if a match or null if no easy match.
     */
    public static HttpMethod lookAheadGet(byte[] bytes, int position, int limit) {
        int length = limit - position;
        if (length < 4)
            return null;
        switch (bytes[position]) {
            case 'G':
                if (bytes[position + 1] == 'E' && bytes[position + 2] == 'T' && bytes[position + 3] == ' ')
                    return GET;
                break;
            case 'P':
                if (bytes[position + 1] == 'O' && bytes[position + 2] == 'S' && bytes[position + 3] == 'T' && length >= 5 && bytes[position + 4] == ' ')
                    return POST;
                if (bytes[position + 1] == 'U' && bytes[position + 2] == 'T' && bytes[position + 3] == ' ')
                    return PUT;
                break;
            case 'H':
                if (bytes[position + 1] == 'E' && bytes[position + 2] == 'A' && bytes[position + 3] == 'D' && length >= 5 && bytes[position + 4] == ' ')
                    return HEAD;
                break;
            case 'O':
                if (bytes[position + 1] == 'O' && bytes[position + 2] == 'T' && bytes[position + 3] == 'I' && length >= 8 &&
                        bytes[position + 4] == 'O' && bytes[position + 5] == 'N' && bytes[position + 6] == 'S' && bytes[position + 7] == ' ')
                    return OPTIONS;
                break;
            case 'D':
                if (bytes[position + 1] == 'E' && bytes[position + 2] == 'L' && bytes[position + 3] == 'E' && length >= 7 &&
                        bytes[position + 4] == 'T' && bytes[position + 5] == 'E' && bytes[position + 6] == ' ')
                    return DELETE;
                break;
            case 'T':
                if (bytes[position + 1] == 'R' && bytes[position + 2] == 'A' && bytes[position + 3] == 'C' && length >= 6 &&
                        bytes[position + 4] == 'E' && bytes[position + 5] == ' ')
                    return TRACE;
                break;
            case 'C':
                if (bytes[position + 1] == 'O' && bytes[position + 2] == 'N' && bytes[position + 3] == 'N' && length >= 8 &&
                        bytes[position + 4] == 'E' && bytes[position + 5] == 'C' && bytes[position + 6] == 'T' && bytes[position + 7] == ' ')
                    return CONNECT;
                break;
            case 'M':
                if (bytes[position + 1] == 'O' && bytes[position + 2] == 'V' && bytes[position + 3] == 'E' && bytes[position + 4] == ' ')
                    return MOVE;
                break;

            default:
                break;
        }
        return null;
    }

    /* ------------------------------------------------------------ */

    /**
     * Optimised lookup to find a method name and trailing space in a byte array.
     *
     * @param buffer buffer containing ISO-8859-1 characters
     * @return A HttpMethod if a match or null if no easy match.
     */
    public static HttpMethod lookAheadGet(ByteBuffer buffer) {
        if (buffer.hasArray())
            return lookAheadGet(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());

        // TODO use cache and check for space
        // return CACHE.getBest(buffer,0,buffer.remaining());
        return null;
    }

    /* ------------------------------------------------------------ */
    public final static Trie<HttpMethod> CACHE = new ArrayTrie<>();

    static {
        for (HttpMethod method : HttpMethod.values())
            CACHE.put(method.toString(), method);
    }

    /* ------------------------------------------------------------ */
    private final ByteBuffer _buffer;
    private final byte[] _bytes;

    /* ------------------------------------------------------------ */
    HttpMethod() {
        _bytes = StringUtil.getBytes(toString());
        _buffer = ByteBuffer.wrap(_bytes);
    }

    /* ------------------------------------------------------------ */
    public byte[] getBytes() {
        return _bytes;
    }

    /* ------------------------------------------------------------ */
    public boolean is(String s) {
        return toString().equalsIgnoreCase(s);
    }

    /* ------------------------------------------------------------ */
    public ByteBuffer asBuffer() {
        return _buffer.asReadOnlyBuffer();
    }

    /* ------------------------------------------------------------ */
    public String asString() {
        return toString();
    }

    /**
     * Converts the given String parameter to an HttpMethod
     *
     * @param method the String to get the equivalent HttpMethod from
     * @return the HttpMethod or null if the parameter method is unknown
     */
    public static HttpMethod fromString(String method) {
        return CACHE.get(method);
    }
}
