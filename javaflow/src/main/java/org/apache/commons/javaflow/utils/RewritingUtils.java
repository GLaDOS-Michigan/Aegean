/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.javaflow.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class RewritingUtils {

    private final static Log log = LogFactory.getLog(RewritingUtils.class);

    public interface Matcher {
        boolean isMatching( final String name );
    }
    
    private final static Matcher MATCH_ALL = new Matcher() {
        public boolean isMatching(final String pName) {
            return true;
        }
    };
    
    /*
     * TODO multiple transformers
     */
    public static void rewriteClassFile(
            final File pInput,
            final ResourceTransformer transformer,
            final File pOutput
            ) throws IOException {

        final byte[] original = toByteArray(pInput);
        byte[] transformed = transformer.transform(original);
        final FileOutputStream os = new FileOutputStream(pOutput);
        os.write(transformed);
        os.close();
    }

    public static boolean rewriteJar(
            final JarInputStream pInput,
            final ResourceTransformer transformer,
            final JarOutputStream pOutput
            ) throws IOException {
        return rewriteJar(pInput, transformer, pOutput, MATCH_ALL);
    }

    public static boolean rewriteJar(
            final JarInputStream pInput,
            final ResourceTransformer transformer,
            final JarOutputStream pOutput,
            final Matcher pMatcher
            ) throws IOException {

        boolean changed = false;

        while(true) {
            final JarEntry entry = pInput.getNextJarEntry();

            if (entry == null) {
                break;
            }

            if (entry.isDirectory()) {
                pOutput.putNextEntry(new JarEntry(entry));
                continue;
            }

            final String name = entry.getName();

            pOutput.putNextEntry(new JarEntry(name));

            if (name.endsWith(".class")) {
                if (pMatcher.isMatching(name)) {

                    if (log.isDebugEnabled()) {
                        log.debug("transforming " + name);
                    }

                    final byte[] original = toByteArray(pInput);

                    byte[] transformed = transformer.transform(original);

                    pOutput.write(transformed);

                    changed |= transformed.length != original.length;

                    continue;
                }
            } else if (name.endsWith(".jar")
                || name.endsWith(".ear")
                || name.endsWith(".zip")
                || name.endsWith(".war")) {

                changed |= rewriteJar(
                        new JarInputStream(pInput),
                        transformer,
                        new JarOutputStream(pOutput),
                        pMatcher
                        );

                continue;
            }

            int length = copy(pInput,pOutput);

            log.debug("copied " + name + "(" + length + ")");
        }

        pInput.close();
        pOutput.close();

        return changed;
    }

    public static byte[] toByteArray(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            return toByteArray(in);
        } finally {
            in.close();
        }
    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(in,baos);
        return baos.toByteArray();
    }

    /**
     * Copies the entire {@link InputStream} to the given {@link OutputStream}.
     *
     * @return
     *      the number of bytes copied.
     */
    public static int copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }


    public static void main(final String[] args) throws FileNotFoundException, IOException {
        ResourceTransformer transformer = new AsmClassTransformer();

        for (int i=0; i<args.length; i+=2) {
            // System.out.println("rewriting " + args[i]);

            RewritingUtils.rewriteJar(
                    new JarInputStream(new FileInputStream(args[i])),
                    transformer,
                    new JarOutputStream(new FileOutputStream(args[i+1]))
                    );
        }

        // System.out.println("done");
        
    }
}
