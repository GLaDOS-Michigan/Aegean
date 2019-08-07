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

import Applications.jetty.util.resource.Resource;

import java.io.*;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/* ------------------------------------------------------------------------------- */

/**
 * Buffer utility methods.
 * <p>The standard JVM {@link ByteBuffer} can exist in two modes: In fill mode the valid
 * data is between 0 and pos; In flush mode the valid data is between the pos and the limit.
 * The various ByteBuffer methods assume a mode and some of them will switch or enforce a mode:
 * Allocate and clear set fill mode; flip and compact switch modes; read and write assume fill
 * and flush modes.    This duality can result in confusing code such as:
 * <pre>
 *     buffer.clear();
 *     channel.write(buffer);
 * </pre>
 * Which looks as if it should write no data, but in fact writes the buffer worth of garbage.
 * </p>
 * <p>
 * The BufferUtil class provides a set of utilities that operate on the convention that ByteBuffers
 * will always be left, passed in an API or returned from a method in the flush mode - ie with
 * valid data between the pos and limit.    This convention is adopted so as to avoid confusion as to
 * what state a buffer is in and to avoid excessive copying of data that can result with the usage
 * of compress.</p>
 * <p>
 * Thus this class provides alternate implementations of {@link #allocate(int)},
 * {@link #allocateDirect(int)} and {@link #clear(ByteBuffer)} that leave the buffer
 * in flush mode.   Thus the following tests will pass:<pre>
 *     ByteBuffer buffer = BufferUtil.allocate(1024);
 *     assert(buffer.remaining()==0);
 *     BufferUtil.clear(buffer);
 *     assert(buffer.remaining()==0);
 * </pre>
 * </p>
 * <p>If the BufferUtil methods {@link #fill(ByteBuffer, byte[], int, int)},
 * {@link #append(ByteBuffer, byte[], int, int)} or {@link #put(ByteBuffer, ByteBuffer)} are used,
 * then the caller does not need to explicitly switch the buffer to fill mode.
 * If the caller wishes to use other ByteBuffer bases libraries to fill a buffer,
 * then they can use explicit calls of #flipToFill(ByteBuffer) and #flipToFlush(ByteBuffer, int)
 * to change modes.  Note because this convention attempts to avoid the copies of compact, the position
 * is not set to zero on each fill cycle and so its value must be remembered:
 * <pre>
 *      int pos = BufferUtil.flipToFill(buffer);
 *      try
 *      {
 *          buffer.put(data);
 *      }
 *      finally
 *      {
 *          flipToFlush(buffer, pos);
 *      }
 * </pre>
 * The flipToFill method will effectively clear the buffer if it is emtpy and will compact the buffer if there is no space.
 */
public class BufferUtil {
    static final int TEMP_BUFFER_SIZE = 4096;
    static final byte SPACE = 0x20;
    static final byte MINUS = '-';
    static final byte[] DIGIT =
            {(byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7', (byte) '8', (byte) '9', (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D',
                    (byte) 'E', (byte) 'F'};

    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.wrap(new byte[0]);

    /* ------------------------------------------------------------ */

    /**
     * Allocate ByteBuffer in flush mode.
     * The position and limit will both be zero, indicating that the buffer is
     * empty and must be flipped before any data is put to it.
     *
     * @param capacity capacity of the allocated ByteBuffer
     * @return Buffer
     */
    public static ByteBuffer allocate(int capacity) {
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        buf.limit(0);
        return buf;
    }

    /* ------------------------------------------------------------ */

    /**
     * Allocate ByteBuffer in flush mode.
     * The position and limit will both be zero, indicating that the buffer is
     * empty and in flush mode.
     *
     * @param capacity capacity of the allocated ByteBuffer
     * @return Buffer
     */
    public static ByteBuffer allocateDirect(int capacity) {
        ByteBuffer buf = ByteBuffer.allocateDirect(capacity);
        buf.limit(0);
        return buf;
    }


    /* ------------------------------------------------------------ */

    /**
     * Clear the buffer to be empty in flush mode.
     * The position and limit are set to 0;
     *
     * @param buffer The buffer to clear.
     */
    public static void clear(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.position(0);
            buffer.limit(0);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Clear the buffer to be empty in fill mode.
     * The position is set to 0 and the limit is set to the capacity.
     *
     * @param buffer The buffer to clear.
     */
    public static void clearToFill(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.position(0);
            buffer.limit(buffer.capacity());
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Flip the buffer to fill mode.
     * The position is set to the first unused position in the buffer
     * (the old limit) and the limit is set to the capacity.
     * If the buffer is empty, then this call is effectively {@link #clearToFill(ByteBuffer)}.
     * If there is no unused space to fill, a {@link ByteBuffer#compact()} is done to attempt
     * to create space.
     * <p>
     * This method is used as a replacement to {@link ByteBuffer#compact()}.
     *
     * @param buffer The buffer to flip
     * @return The position of the valid data before the flipped position. This value should be
     * passed to a subsequent call to {@link #flipToFlush(ByteBuffer, int)}
     */
    public static int flipToFill(ByteBuffer buffer) {
        int position = buffer.position();
        int limit = buffer.limit();
        if (position == limit) {
            buffer.position(0);
            buffer.limit(buffer.capacity());
            return 0;
        }

        int capacity = buffer.capacity();
        if (limit == capacity) {
            buffer.compact();
            return 0;
        }

        buffer.position(limit);
        buffer.limit(capacity);
        return position;
    }


    /* ------------------------------------------------------------ */

    /**
     * Flip the buffer to Flush mode.
     * The limit is set to the first unused byte(the old position) and
     * the position is set to the passed position.
     * <p>
     * This method is used as a replacement of {@link Buffer#flip()}.
     *
     * @param buffer   the buffer to be flipped
     * @param position The position of valid data to flip to. This should
     *                 be the return value of the previous call to {@link #flipToFill(ByteBuffer)}
     */
    public static void flipToFlush(ByteBuffer buffer, int position) {
        buffer.limit(buffer.position());
        buffer.position(position);
    }


    /* ------------------------------------------------------------ */

    /**
     * Convert a ByteBuffer to a byte array.
     *
     * @param buffer The buffer to convert in flush mode. The buffer is not altered.
     * @return An array of bytes duplicated from the buffer.
     */
    public static byte[] toArray(ByteBuffer buffer) {
        byte[] to = new byte[buffer.remaining()];
        if (buffer.hasArray()) {
            byte[] array = buffer.array();
            System.arraycopy(array, buffer.arrayOffset() + buffer.position(), to, 0, to.length);
        } else
            buffer.slice().get(to);
        return to;
    }

    /* ------------------------------------------------------------ */

    /**
     * Check for an empty or null buffer.
     *
     * @param buf the buffer to check
     * @return true if the buffer is null or empty.
     */
    public static boolean isEmpty(ByteBuffer buf) {
        return buf == null || buf.remaining() == 0;
    }

    /* ------------------------------------------------------------ */

    /**
     * Check for a non null and non empty buffer.
     *
     * @param buf the buffer to check
     * @return true if the buffer is not null and not empty.
     */
    public static boolean hasContent(ByteBuffer buf) {
        return buf != null && buf.remaining() > 0;
    }

    /* ------------------------------------------------------------ */

    /**
     * Check for a non null and full buffer.
     *
     * @param buf the buffer to check
     * @return true if the buffer is not null and the limit equals the capacity.
     */
    public static boolean isFull(ByteBuffer buf) {
        return buf != null && buf.limit() == buf.capacity();
    }

    /* ------------------------------------------------------------ */

    /**
     * Get remaining from null checked buffer
     *
     * @param buffer The buffer to get the remaining from, in flush mode.
     * @return 0 if the buffer is null, else the bytes remaining in the buffer.
     */
    public static int length(ByteBuffer buffer) {
        return buffer == null ? 0 : buffer.remaining();
    }

    /* ------------------------------------------------------------ */

    /**
     * Get the space from the limit to the capacity
     *
     * @param buffer the buffer to get the space from
     * @return space
     */
    public static int space(ByteBuffer buffer) {
        if (buffer == null)
            return 0;
        return buffer.capacity() - buffer.limit();
    }

    /* ------------------------------------------------------------ */

    /**
     * Compact the buffer
     *
     * @param buffer the buffer to compact
     * @return true if the compact made a full buffer have space
     */
    public static boolean compact(ByteBuffer buffer) {
        boolean full = buffer.limit() == buffer.capacity();
        buffer.compact().flip();
        return full && buffer.limit() < buffer.capacity();
    }

    /* ------------------------------------------------------------ */

    /**
     * Put data from one buffer into another, avoiding over/under flows
     *
     * @param from Buffer to take bytes from in flush mode
     * @param to   Buffer to put bytes to in fill mode.
     * @return number of bytes moved
     */
    public static int put(ByteBuffer from, ByteBuffer to) {
        int put;
        int remaining = from.remaining();
        if (remaining > 0) {
            if (remaining <= to.remaining()) {
                to.put(from);
                put = remaining;
                from.position(0);
                from.limit(0);
            } else if (from.hasArray()) {
                put = to.remaining();
                to.put(from.array(), from.arrayOffset() + from.position(), put);
                from.position(from.position() + put);
            } else {
                put = to.remaining();
                ByteBuffer slice = from.slice();
                slice.limit(put);
                to.put(slice);
                from.position(from.position() + put);
            }
        } else
            put = 0;

        return put;
    }

    /* ------------------------------------------------------------ */

    /**
     * Put data from one buffer into another, avoiding over/under flows
     *
     * @param from Buffer to take bytes from in flush mode
     * @param to   Buffer to put bytes to in flush mode. The buffer is flipToFill before the put and flipToFlush after.
     * @return number of bytes moved
     */
    public static int flipPutFlip(ByteBuffer from, ByteBuffer to) {
        int pos = flipToFill(to);
        try {
            return put(from, to);
        } finally {
            flipToFlush(to, pos);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Append bytes to a buffer.
     */
    public static void append(ByteBuffer to, byte[] b, int off, int len) throws BufferOverflowException {
        int pos = flipToFill(to);
        try {
            to.put(b, off, len);
        } finally {
            flipToFlush(to, pos);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Appends a byte to a buffer
     */
    public static void append(ByteBuffer to, byte b) {
        int pos = flipToFill(to);
        try {
            to.put(b);
        } finally {
            flipToFlush(to, pos);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Like append, but does not throw {@link BufferOverflowException}
     */
    public static int fill(ByteBuffer to, byte[] b, int off, int len) {
        int pos = flipToFill(to);
        try {
            int remaining = to.remaining();
            int take = remaining < len ? remaining : len;
            to.put(b, off, take);
            return take;
        } finally {
            flipToFlush(to, pos);
        }
    }


    /* ------------------------------------------------------------ */
    public static void readFrom(File file, ByteBuffer buffer) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel channel = raf.getChannel();
            long needed = raf.length();

            while (needed > 0 && buffer.hasRemaining())
                needed = needed - channel.read(buffer);
        }
    }

    /* ------------------------------------------------------------ */
    public static void readFrom(InputStream is, int needed, ByteBuffer buffer) throws IOException {
        ByteBuffer tmp = allocate(8192);

        while (needed > 0 && buffer.hasRemaining()) {
            int l = is.read(tmp.array(), 0, 8192);
            if (l < 0)
                break;
            tmp.position(0);
            tmp.limit(l);
            buffer.put(tmp);
        }
    }

    /* ------------------------------------------------------------ */
    public static void writeTo(ByteBuffer buffer, OutputStream out) throws IOException {
        if (buffer.hasArray())
            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        else {
            byte[] bytes = new byte[TEMP_BUFFER_SIZE];
            while (buffer.hasRemaining()) {
                int byteCountToWrite = Math.min(buffer.remaining(), TEMP_BUFFER_SIZE);
                buffer.get(bytes, 0, byteCountToWrite);
                out.write(bytes, 0, byteCountToWrite);
            }
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * Convert the buffer to an ISO-8859-1 String
     *
     * @param buffer The buffer to convert in flush mode. The buffer is unchanged
     * @return The buffer as a string.
     */
    public static String toString(ByteBuffer buffer) {
        return toString(buffer, StandardCharsets.ISO_8859_1);
    }

    /* ------------------------------------------------------------ */

    /**
     * Convert the buffer to an UTF-8 String
     *
     * @param buffer The buffer to convert in flush mode. The buffer is unchanged
     * @return The buffer as a string.
     */
    public static String toUTF8String(ByteBuffer buffer) {
        return toString(buffer, StandardCharsets.UTF_8);
    }

    /* ------------------------------------------------------------ */

    /**
     * Convert the buffer to an ISO-8859-1 String
     *
     * @param buffer  The buffer to convert in flush mode. The buffer is unchanged
     * @param charset The {@link Charset} to use to convert the bytes
     * @return The buffer as a string.
     */
    public static String toString(ByteBuffer buffer, Charset charset) {
        if (buffer == null)
            return null;
        byte[] array = buffer.hasArray() ? buffer.array() : null;
        if (array == null) {
            byte[] to = new byte[buffer.remaining()];
            buffer.slice().get(to);
            return new String(to, 0, to.length, charset);
        }
        return new String(array, buffer.arrayOffset() + buffer.position(), buffer.remaining(), charset);
    }

    /* ------------------------------------------------------------ */

    /**
     * Convert a partial buffer to an ISO-8859-1 String
     *
     * @param buffer  The buffer to convert in flush mode. The buffer is unchanged
     * @param charset The {@link Charset} to use to convert the bytes
     * @return The buffer as a string.
     */
    public static String toString(ByteBuffer buffer, int position, int length, Charset charset) {
        if (buffer == null)
            return null;
        byte[] array = buffer.hasArray() ? buffer.array() : null;
        if (array == null) {
            ByteBuffer ro = buffer.asReadOnlyBuffer();
            ro.position(position);
            ro.limit(position + length);
            byte[] to = new byte[length];
            ro.get(to);
            return new String(to, 0, to.length, charset);
        }
        return new String(array, buffer.arrayOffset() + position, length, charset);
    }

    /* ------------------------------------------------------------ */

    /**
     * Convert buffer to an integer. Parses up to the first non-numeric character. If no number is found an IllegalArgumentException is thrown
     *
     * @param buffer A buffer containing an integer in flush mode. The position is not changed.
     * @return an int
     */
    public static int toInt(ByteBuffer buffer) {
        int val = 0;
        boolean started = false;
        boolean minus = false;

        for (int i = buffer.position(); i < buffer.limit(); i++) {
            byte b = buffer.get(i);
            if (b <= SPACE) {
                if (started)
                    break;
            } else if (b >= '0' && b <= '9') {
                val = val * 10 + (b - '0');
                started = true;
            } else if (b == MINUS && !started) {
                minus = true;
            } else
                break;
        }

        if (started)
            return minus ? (-val) : val;
        throw new NumberFormatException(toString(buffer));
    }

    /**
     * Convert buffer to an long. Parses up to the first non-numeric character. If no number is found an IllegalArgumentException is thrown
     *
     * @param buffer A buffer containing an integer in flush mode. The position is not changed.
     * @return an int
     */
    public static long toLong(ByteBuffer buffer) {
        long val = 0;
        boolean started = false;
        boolean minus = false;

        for (int i = buffer.position(); i < buffer.limit(); i++) {
            byte b = buffer.get(i);
            if (b <= SPACE) {
                if (started)
                    break;
            } else if (b >= '0' && b <= '9') {
                val = val * 10L + (b - '0');
                started = true;
            } else if (b == MINUS && !started) {
                minus = true;
            } else
                break;
        }

        if (started)
            return minus ? (-val) : val;
        throw new NumberFormatException(toString(buffer));
    }

    public static void putHexInt(ByteBuffer buffer, int n) {
        if (n < 0) {
            buffer.put((byte) '-');

            if (n == Integer.MIN_VALUE) {
                buffer.put((byte) (0x7f & '8'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));
                buffer.put((byte) (0x7f & '0'));

                return;
            }
            n = -n;
        }

        if (n < 0x10) {
            buffer.put(DIGIT[n]);
        } else {
            boolean started = false;
            // This assumes constant time int arithmatic
            for (int hexDivisor : hexDivisors) {
                if (n < hexDivisor) {
                    if (started)
                        buffer.put((byte) '0');
                    continue;
                }

                started = true;
                int d = n / hexDivisor;
                buffer.put(DIGIT[d]);
                n = n - d * hexDivisor;
            }
        }
    }

    /* ------------------------------------------------------------ */
    public static void putDecInt(ByteBuffer buffer, int n) {
        if (n < 0) {
            buffer.put((byte) '-');

            if (n == Integer.MIN_VALUE) {
                buffer.put((byte) '2');
                n = 147483648;
            } else
                n = -n;
        }

        if (n < 10) {
            buffer.put(DIGIT[n]);
        } else {
            boolean started = false;
            // This assumes constant time int arithmatic
            for (int decDivisor : decDivisors) {
                if (n < decDivisor) {
                    if (started)
                        buffer.put((byte) '0');
                    continue;
                }

                started = true;
                int d = n / decDivisor;
                buffer.put(DIGIT[d]);
                n = n - d * decDivisor;
            }
        }
    }

    public static void putDecLong(ByteBuffer buffer, long n) {
        if (n < 0) {
            buffer.put((byte) '-');

            if (n == Long.MIN_VALUE) {
                buffer.put((byte) '9');
                n = 223372036854775808L;
            } else
                n = -n;
        }

        if (n < 10) {
            buffer.put(DIGIT[(int) n]);
        } else {
            boolean started = false;
            // This assumes constant time int arithmatic
            for (long aDecDivisorsL : decDivisorsL) {
                if (n < aDecDivisorsL) {
                    if (started)
                        buffer.put((byte) '0');
                    continue;
                }

                started = true;
                long d = n / aDecDivisorsL;
                buffer.put(DIGIT[(int) d]);
                n = n - d * aDecDivisorsL;
            }
        }
    }

    public static ByteBuffer toBuffer(int value) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        putDecInt(buf, value);
        return buf;
    }

    public static ByteBuffer toBuffer(long value) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        putDecLong(buf, value);
        return buf;
    }

    public static ByteBuffer toBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static ByteBuffer toDirectBuffer(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes);
        buf.flip();
        return buf;
    }

    public static ByteBuffer toBuffer(String s, Charset charset) {
        return ByteBuffer.wrap(s.getBytes(charset));
    }

    public static ByteBuffer toDirectBuffer(String s, Charset charset) {
        byte[] bytes = s.getBytes(charset);
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes);
        buf.flip();
        return buf;
    }

    /**
     * Create a new ByteBuffer using provided byte array.
     *
     * @param array the byte array to back buffer with.
     * @return ByteBuffer with provided byte array, in flush mode
     */
    public static ByteBuffer toBuffer(byte array[]) {
        return ByteBuffer.wrap(array);
    }

    /**
     * Create a new ByteBuffer using the provided byte array.
     *
     * @param array  the byte array to use.
     * @param offset the offset within the byte array to use from
     * @param length the length in bytes of the array to use
     * @return ByteBuffer with provided byte array, in flush mode
     */
    public static ByteBuffer toBuffer(byte array[], int offset, int length) {
        return ByteBuffer.wrap(array, offset, length);
    }

    public static ByteBuffer toMappedBuffer(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return raf.getChannel().map(MapMode.READ_ONLY, 0, raf.length());
        }
    }

    public static ByteBuffer toBuffer(Resource resource, boolean direct) throws IOException {
        int len = (int) resource.length();
        if (len < 0)
            throw new IllegalArgumentException("invalid resource: " + String.valueOf(resource) + " len=" + len);

        ByteBuffer buffer = direct ? BufferUtil.allocateDirect(len) : BufferUtil.allocate(len);

        int pos = BufferUtil.flipToFill(buffer);
        if (resource.getFile() != null)
            BufferUtil.readFrom(resource.getFile(), buffer);
        else {
            try (InputStream is = resource.getInputStream();) {
                BufferUtil.readFrom(is, len, buffer);
            }
        }
        BufferUtil.flipToFlush(buffer, pos);

        return buffer;
    }

    public static String toSummaryString(ByteBuffer buffer) {
        if (buffer == null)
            return "null";
        StringBuilder buf = new StringBuilder();
        buf.append("[p=");
        buf.append(buffer.position());
        buf.append(",l=");
        buf.append(buffer.limit());
        buf.append(",c=");
        buf.append(buffer.capacity());
        buf.append(",r=");
        buf.append(buffer.remaining());
        buf.append("]");
        return buf.toString();
    }

    public static String toDetailString(ByteBuffer[] buffer) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < buffer.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(toDetailString(buffer[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    public static String toDetailString(ByteBuffer buffer) {
        if (buffer == null)
            return "null";

        StringBuilder buf = new StringBuilder();
        buf.append(buffer.getClass().getSimpleName());
        buf.append("@");
        if (buffer.hasArray())
            buf.append(Integer.toHexString(((Object) buffer.array()).hashCode()));
        else
            buf.append(Integer.toHexString(buf.hashCode()));
        buf.append("[p=");
        buf.append(buffer.position());
        buf.append(",l=");
        buf.append(buffer.limit());
        buf.append(",c=");
        buf.append(buffer.capacity());
        buf.append(",r=");
        buf.append(buffer.remaining());
        buf.append("]={");

        for (int i = 0; i < buffer.position(); i++) {
            char c = (char) buffer.get(i);
            if (c >= ' ' && c <= 127)
                buf.append(c);
            else if (c == '\r' || c == '\n')
                buf.append('|');
            else
                buf.append('\ufffd');
            if (i == 16 && buffer.position() > 32) {
                buf.append("...");
                i = buffer.position() - 16;
            }
        }
        buf.append("<<<");
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            char c = (char) buffer.get(i);
            if (c >= ' ' && c <= 127)
                buf.append(c);
            else if (c == '\r' || c == '\n')
                buf.append('|');
            else
                buf.append('\ufffd');
            if (i == buffer.position() + 16 && buffer.limit() > buffer.position() + 32) {
                buf.append("...");
                i = buffer.limit() - 16;
            }
        }
        buf.append(">>>");
        int limit = buffer.limit();
        buffer.limit(buffer.capacity());
        for (int i = limit; i < buffer.capacity(); i++) {
            char c = (char) buffer.get(i);
            if (c >= ' ' && c <= 127)
                buf.append(c);
            else if (c == '\r' || c == '\n')
                buf.append('|');
            else
                buf.append('\ufffd');
            if (i == limit + 16 && buffer.capacity() > limit + 32) {
                buf.append("...");
                i = buffer.capacity() - 16;
            }
        }
        buffer.limit(limit);
        buf.append("}");

        return buf.toString();
    }


    private final static int[] decDivisors =
            {1000000000, 100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1};

    private final static int[] hexDivisors =
            {0x10000000, 0x1000000, 0x100000, 0x10000, 0x1000, 0x100, 0x10, 0x1};

    private final static long[] decDivisorsL =
            {1000000000000000000L, 100000000000000000L, 10000000000000000L, 1000000000000000L, 100000000000000L, 10000000000000L, 1000000000000L, 100000000000L,
                    10000000000L, 1000000000L, 100000000L, 10000000L, 1000000L, 100000L, 10000L, 1000L, 100L, 10L, 1L};

    public static void putCRLF(ByteBuffer buffer) {
        buffer.put((byte) 13);
        buffer.put((byte) 10);
    }

    public static boolean isPrefix(ByteBuffer prefix, ByteBuffer buffer) {
        if (prefix.remaining() > buffer.remaining())
            return false;
        int bi = buffer.position();
        for (int i = prefix.position(); i < prefix.limit(); i++)
            if (prefix.get(i) != buffer.get(bi++))
                return false;
        return true;
    }


}
