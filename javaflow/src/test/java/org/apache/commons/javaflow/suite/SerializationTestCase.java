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
package org.apache.commons.javaflow.suite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Proxy;

import junit.framework.TestCase;
import junitx.util.PrivateAccessor;

import org.apache.commons.javaflow.Continuation;
import org.apache.commons.javaflow.bytecode.StackRecorder;
import org.apache.commons.javaflow.rewrite.Invoker;
import org.apache.commons.javaflow.rewrite.Simple;
import org.apache.commons.javaflow.rewrite.SimpleSerializable;

@SuppressWarnings("unchecked")
public final class SerializationTestCase extends TestCase {

    private File output;

    private boolean fromSuite() {
        final String cl = this.getClass().getClassLoader().getClass().getName();
        return cl.contains("ClassTransformerClassLoader");
    }

    public void testSuspend() throws Exception {
        assertTrue(fromSuite());
        final SimpleSerializable r = new SimpleSerializable();
        assertTrue(r.g == -1);
        assertTrue(r.l == -1);
        Continuation c1 = Continuation.startWith(r);
        assertTrue(r.g == 0);
        assertTrue(r.l == 0);

        output = File.createTempFile("continuation", "xml");
        output.deleteOnExit();

        saveJDK(c1, output);

    }

    public class ObjectInputStreamExt extends ObjectInputStream {

        private ClassLoader classloader;

        public ObjectInputStreamExt(InputStream in, ClassLoader loader) throws IOException {
            super(in);
            this.classloader = loader;
        }

        @SuppressWarnings("rawtypes")
        protected Class resolveClass(ObjectStreamClass classDesc) throws IOException, ClassNotFoundException {

            return Class.forName(classDesc.getName(), true, classloader);
        }

        @SuppressWarnings("rawtypes")
        protected Class resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            Class[] cinterfaces = new Class[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                cinterfaces[i] = Class.forName(interfaces[i], true, classloader);
            }

            try {
                return Proxy.getProxyClass(classloader, cinterfaces);
            } catch (IllegalArgumentException e) {
                throw new ClassNotFoundException(null, e);
            }
        }
    }


    private void saveJDK(final Object c1, final File output) throws IOException {
        final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(output));
        oos.writeObject(c1);
        oos.close();
    }

    private Object loadJDK(final File input) throws IOException, ClassNotFoundException {
        final ObjectInputStream ois = new ObjectInputStreamExt(new FileInputStream(input), this.getClass().getClassLoader());
        final Object o = ois.readObject();
        ois.close();
        return o;
    }

    public void testResume() throws Exception {
        assertTrue(fromSuite());
        testSuspend();
        assertTrue("suspend must succeed to create the output first", output != null);

        assertEquals(output.length(), 562);

        final Object o = loadJDK(output);

        assertTrue(o instanceof Continuation);
        final Continuation c1 = (Continuation) o;
        final StackRecorder sr1 = (StackRecorder) PrivateAccessor.getField(c1,"stackRecorder");
        final Runnable r1 = (Runnable) PrivateAccessor.getField(sr1, "runnable");
        assertEquals(SimpleSerializable.class.getName(), r1.getClass().getName());

        final SimpleSerializable ss1 = (SimpleSerializable)r1;
        assertTrue(ss1.g == 0);
        assertTrue(ss1.l == 0);
        final Continuation c2 = Continuation.continueWith(c1);
        final StackRecorder sr2 = (StackRecorder) PrivateAccessor.getField(c2,"stackRecorder");
        final Runnable r2 = (Runnable) PrivateAccessor.getField(sr2, "runnable");
        assertEquals(SimpleSerializable.class.getName(), r2.getClass().getName());
        final SimpleSerializable ss2 = (SimpleSerializable)r2;
        assertTrue(ss2.g == 1);
        assertTrue(ss2.l == 1);
        assertTrue(r1 == r2);
    }


    public void testSerializableCheck() throws Exception {
        assertTrue(fromSuite());
        final Runnable r1 = new Simple();
        Continuation c1 = Continuation.startWith(r1);
        assertTrue(c1 != null);
        assertTrue(!c1.isSerializable());

        final Runnable r2 = new SimpleSerializable();
        Continuation c2 = Continuation.startWith(r2);
        assertTrue(c2 != null);
        assertTrue(c2.isSerializable());

        final Runnable r3 = new SimpleSerializable();
        Continuation c3 = Continuation.startWith(new Invoker(r3));
        assertTrue(c3 != null);
        assertTrue(c3.isSerializable());
        // the invoker should not appear on the stack as it should not be instrumented
        // and by that not hinder serialization (in cocoon the continuation is always called
        // through such an invoker)
    }
}
