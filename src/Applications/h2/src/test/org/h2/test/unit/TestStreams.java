/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import org.h2.compress.LZFInputStream;
import org.h2.compress.LZFOutputStream;
import org.h2.test.TestBase;

import java.io.*;
import java.util.Random;

/**
 * Tests the LZF stream.
 */
public class TestStreams extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws IOException {
        testLZFStreams();
        testLZFStreamClose();
    }

    private byte[] getRandomBytes(Random random) {
        int[] sizes = new int[]{0, 1, random.nextInt(1000), random.nextInt(100000), random.nextInt(1000000)};
        int size = sizes[random.nextInt(sizes.length)];
        byte[] buffer = new byte[size];
        if (random.nextInt(5) == 1) {
            random.nextBytes(buffer);
        } else if (random.nextBoolean()) {
            int patternLen = random.nextInt(100) + 1;
            for (int j = 0; j < size; j++) {
                buffer[j] = (byte) (j % patternLen);
            }
        }
        return buffer;
    }

    private void testLZFStreamClose() throws IOException {
        String fileName = getTestDir("") + "/temp";
        LZFOutputStream out = new LZFOutputStream(new FileOutputStream(fileName));
        out.write("Hello".getBytes());
        out.close();
        LZFInputStream in = new LZFInputStream(new FileInputStream(fileName));
        byte[] buff = new byte[100];
        assertEquals(5, in.read(buff));
        in.read();
        in.close();
    }

    private void testLZFStreams() throws IOException {
        Random random = new Random(1);
        int max = getSize(100, 1000);
        for (int i = 0; i < max; i += 3) {
            byte[] buffer = getRandomBytes(random);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            LZFOutputStream comp = new LZFOutputStream(out);
            if (random.nextInt(10) == 1) {
                comp.write(buffer);
            } else {
                for (int j = 0; j < buffer.length; ) {
                    int[] sizes = new int[]{0, 1, random.nextInt(100), random.nextInt(100000)};
                    int size = sizes[random.nextInt(sizes.length)];
                    size = Math.min(size, buffer.length - j);
                    if (size == 1) {
                        comp.write(buffer[j]);
                    } else {
                        comp.write(buffer, j, size);
                    }
                    j += size;
                }
            }
            comp.close();
            byte[] compressed = out.toByteArray();
            ByteArrayInputStream in = new ByteArrayInputStream(compressed);
            LZFInputStream decompress = new LZFInputStream(in);
            byte[] test = new byte[buffer.length];
            for (int j = 0; j < buffer.length; ) {
                int[] sizes = new int[]{0, 1, random.nextInt(100), random.nextInt(100000)};
                int size = sizes[random.nextInt(sizes.length)];
                if (size == 1) {
                    int x = decompress.read();
                    if (x < 0) {
                        break;
                    }
                    test[j++] = (byte) x;
                } else {
                    size = Math.min(size, test.length - j);
                    int l = decompress.read(test, j, size);
                    if (l < 0) {
                        break;
                    }
                    j += l;
                }
            }
            assertEquals(buffer, test);
        }
    }

}
