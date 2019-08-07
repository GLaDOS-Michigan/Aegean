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
package org.apache.commons.javaflow.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.javaflow.bytecode.BytecodeClassLoader;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

public final class ClassTransformerClassLoader extends BytecodeClassLoader {

    private final ResourceTransformer transformer;
    private final Set<String> instrument;
    private final Set<String> load;

    public ClassTransformerClassLoader(final ResourceTransformer pTransformer, final Class<?>[] pInstrument, final Class<?>[] pLoad) {
        
        instrument = new HashSet<String>(pInstrument.length);
        for (int i = 0; i < pInstrument.length; i++) {
            instrument.add(pInstrument[i].getName());
        }

        load = new HashSet<String>(pLoad.length);
        for (int i = 0; i < pLoad.length; i++) {
            load.add(pLoad[i].getName());
        }
        
        transformer = pTransformer;
    }

    protected byte[] transform(final String pName, final InputStream pClassStream) throws IOException {
        final byte[] oldClass = IOUtils.toByteArray(pClassStream);
        final byte[] newClass = transformer.transform(oldClass);

        // CheckClassAdapter.verify(new ClassReader(newClass), true);

        // Test case output has been moved to target/test-instrumentation to reduce clutter in main directory
        new File("target/test-instrumentation").mkdirs();

        CheckClassAdapter.verify(new ClassReader(newClass), true, new PrintWriter(
            new FileOutputStream("target/test-instrumentation/" + transformer.getClass().getSimpleName()
                + "_" + pName + ".new.check")));

        new ClassReader(oldClass).accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(
            new FileOutputStream("target/test-instrumentation/" + transformer.getClass().getSimpleName() 
                + "_" + pName + ".old"))), 0);

        new ClassReader(newClass).accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(
            new FileOutputStream("target/test-instrumentation/" + transformer.getClass().getSimpleName()
                + "_" + pName + ".new"))), 0);

        return newClass;
    }

    
    public Class<?> loadClass( final String name ) throws ClassNotFoundException {
        
        final int i = name.indexOf('$');
        final String key;
        
        if (i == -1) {
            key = name;
        } else {
            key = name.substring(0, i);
        }
        
        if (instrument.contains(key) || load.contains(key)) {

            try {
                final InputStream is = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class");


                final byte[] bytecode;
                
                if (instrument.contains(key)) {
                    // System.err.println("Instrumenting: " + name);
                    bytecode = transform(name, is);
                } else {
                    // System.err.println("Loading: " + name);
                    bytecode = new ClassReader(is).b;                   
                }
                
                return super.defineClass(name, bytecode, 0, bytecode.length);

            } catch (Throwable ex) {
                // System.err.println("Load error: " + ex.toString());
                ex.printStackTrace();
                throw new ClassNotFoundException(name + " " + ex.getMessage(), ex);
            }
        }

        // System.err.println("Delegating: " + name);
        return super.loadClass(name);
    }
}
