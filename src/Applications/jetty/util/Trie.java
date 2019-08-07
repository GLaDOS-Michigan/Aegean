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

package Applications.jetty.util;

import java.nio.ByteBuffer;
import java.util.Set;


/* ------------------------------------------------------------ */

/**
 * A Trie String lookup data structure.
 *
 * @param <V>
 */
public interface Trie<V> {
    /* ------------------------------------------------------------ */

    /**
     * Put and entry into the Trie
     *
     * @param s The key for the entry
     * @param v The value of the entry
     * @return True if the Trie had capacity to add the field.
     */
    public boolean put(String s, V v);
    
    /* ------------------------------------------------------------ */

    /**
     * Put a value as both a key and a value.
     *
     * @param v The value and key
     * @return True if the Trie had capacity to add the field.
     */
    public boolean put(V v);

    /* ------------------------------------------------------------ */
    public V remove(String s);

    /* ------------------------------------------------------------ */

    /**
     * Get and exact match from a String key
     *
     * @param s The key
     */
    public V get(String s);

    /* ------------------------------------------------------------ */

    /**
     * Get and exact match from a String key
     *
     * @param s      The key
     * @param offset The offset within the string of the key
     * @param len    the length of the key
     */
    public V get(String s, int offset, int len);

    /* ------------------------------------------------------------ */

    /**
     * Get and exact match from a segment of a ByteBuufer as key
     *
     * @param b The buffer
     * @return The value or null if not found
     */
    public V get(ByteBuffer b);

    /* ------------------------------------------------------------ */

    /**
     * Get and exact match from a segment of a ByteBuufer as key
     *
     * @param b      The buffer
     * @param offset The offset within the buffer of the key
     * @param len    the length of the key
     * @return The value or null if not found
     */
    public V get(ByteBuffer b, int offset, int len);
    
    /* ------------------------------------------------------------ */

    /**
     * Get the best match from key in a String.
     *
     * @param s The string
     * @return The value or null if not found
     */
    public V getBest(String s);
    
    /* ------------------------------------------------------------ */

    /**
     * Get the best match from key in a String.
     *
     * @param s      The string
     * @param offset The offset within the string of the key
     * @param len    the length of the key
     * @return The value or null if not found
     */
    public V getBest(String s, int offset, int len);

    /* ------------------------------------------------------------ */

    /**
     * Get the best match from key in a byte array.
     * The key is assumed to by ISO_8859_1 characters.
     *
     * @param b      The buffer
     * @param offset The offset within the array of the key
     * @param len    the length of the key
     * @return The value or null if not found
     */
    public V getBest(byte[] b, int offset, int len);

    /* ------------------------------------------------------------ */

    /**
     * Get the best match from key in a byte buffer.
     * The key is assumed to by ISO_8859_1 characters.
     *
     * @param b      The buffer
     * @param offset The offset within the buffer of the key
     * @param len    the length of the key
     * @return The value or null if not found
     */
    public V getBest(ByteBuffer b, int offset, int len);

    /* ------------------------------------------------------------ */
    public Set<String> keySet();

    /* ------------------------------------------------------------ */
    public boolean isFull();

    /* ------------------------------------------------------------ */
    public boolean isCaseInsensitive();

}
