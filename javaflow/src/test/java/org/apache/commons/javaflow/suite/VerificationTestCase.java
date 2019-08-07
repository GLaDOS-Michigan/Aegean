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

import junit.framework.TestCase;

import org.apache.commons.javaflow.Continuation;
import org.apache.commons.javaflow.rewrite.BlackRed;
import org.apache.commons.javaflow.rewrite.ClassAccess1;
import org.apache.commons.javaflow.rewrite.ClassAccess2;
import org.apache.commons.javaflow.rewrite.ClassAccess3;
import org.apache.commons.javaflow.rewrite.ConstructorInvocation;
import org.apache.commons.javaflow.rewrite.CounterFlow;
import org.apache.commons.javaflow.rewrite.DefaultConstructor;
import org.apache.commons.javaflow.rewrite.Invoker;
import org.apache.commons.javaflow.rewrite.NestedSynchronized;
import org.apache.commons.javaflow.rewrite.NewObject;
import org.apache.commons.javaflow.rewrite.NoReference;
import org.apache.commons.javaflow.rewrite.NullLocalVariable;
import org.apache.commons.javaflow.rewrite.NullVariableMethodFlow;
import org.apache.commons.javaflow.rewrite.RewriteBugs;
import org.apache.commons.javaflow.rewrite.Simple;
import org.apache.commons.javaflow.rewrite.SimpleSerializable;
import org.apache.commons.javaflow.rewrite.SimpleSynchronized;
import org.apache.commons.javaflow.rewrite.SimpleTryCatch;
import org.apache.commons.javaflow.rewrite.Stack;

public final class VerificationTestCase extends TestCase {

    private boolean fromSuite() {
        final String cl = this.getClass().getClassLoader().getClass().getName();
        return cl.contains("ClassTransformerClassLoader");
    }

    public void testBlackRed2() {
        assertTrue(fromSuite());
        final Runnable r = new BlackRed();
        final Continuation c1 = Continuation.startWith(r);
        assertTrue(c1 != null);
        final Continuation c2 = Continuation.continueWith(c1);
        assertTrue(c2 == null);
    }

    public void testClassAccess1() throws Exception {
        assertTrue(fromSuite());
        final ClassAccess1 r = new ClassAccess1();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
    }

    public void testClassAccess2() throws Exception {
        assertTrue(fromSuite());
        final ClassAccess2 r = new ClassAccess2();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
    }

    public void testClassAccess3() throws Exception {
        assertTrue(fromSuite());
        final ClassAccess3 r = new ClassAccess3();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
    }

    public void testCounter() {
        assertTrue(fromSuite());
        final int count = 5;
        final Runnable r = new CounterFlow(count);
        int i = 0;
        Continuation c = Continuation.startWith(r);
        while (c != null) {
            c = Continuation.continueWith(c);
            i++;
        }
        assertTrue(i == count);
    }

    public void testInvoker() {
        assertTrue(fromSuite());
        Runnable o = new DefaultConstructor();
        Continuation c = Continuation.startWith(o);
        assertTrue(c == null);
    }

    public void testInvoker2() {
        assertTrue(fromSuite());
        final Runnable r = new Simple();
        final Runnable o = new Invoker(r);
        final Continuation c = Continuation.startWith(o);
        assertNotNull(c);
    }

    public void testConstructorInvocation() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new ConstructorInvocation();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
        final Continuation c2 = Continuation.continueWith(c);
        assertTrue(c2 == null);
    }

    public void testNewObject() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new NewObject();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c == null);
    }

    public void testNullVariableMethodFlow() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new NullVariableMethodFlow();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c == null);
    }

    public void testNullLocalVariable() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new NullLocalVariable();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c == null);
    }

    public void testNoReference() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new NoReference();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
    }

    public void testSimpleSuspendResume() throws Exception {
        assertTrue(fromSuite());
        final SimpleSerializable r = new SimpleSerializable();
        assertTrue(r.g == -1);
        assertTrue(r.l == -1);
        Continuation c1 = Continuation.startWith(r);
        assertNotNull(c1);
        assertTrue(r.g == 0);
        assertTrue(r.l == 0);
        Continuation c2 = Continuation.continueWith(c1);
        assertNotNull(c2);
        assertTrue(r.g == 1);
        assertTrue(r.l == 1);
        Continuation c3 = Continuation.continueWith(c2);
        assertNotNull(c3);
        assertTrue(r.g == 2);
        assertTrue(r.l == 2);
    }

    public void testContinuationBranching() throws Exception {
        assertTrue(fromSuite());
        final SimpleSerializable r = new SimpleSerializable();
        assertTrue(r.g == -1);
        assertTrue(r.l == -1);
        Continuation c1 = Continuation.startWith(r);
        assertNotNull(c1);
        assertTrue(r.g == 0);
        assertTrue(r.l == 0);
        Continuation c2 = Continuation.continueWith(c1);
        assertNotNull(c2);
        assertTrue(r.g == 1);
        assertTrue(r.l == 1);
        Continuation c31 = Continuation.continueWith(c2);
        assertNotNull(c31);
        assertTrue(r.g == 2);
        assertTrue(r.l == 2);
        Continuation c32 = Continuation.continueWith(c2);
        assertNotNull(c32);
        assertTrue(r.g == 3);
        assertTrue(r.l == 2);
    }

    public void testSimpleSuspend() throws Exception {
        assertTrue(fromSuite());
        final Simple r = new Simple();
        final Continuation c = Continuation.startWith(r);
        assertTrue(c != null);
    }

    public void testSimpleTryCatchWithoutException() throws Exception {
        assertTrue(fromSuite());
        final SimpleTryCatch r = new SimpleTryCatch(false);
        Continuation c;

        c = Continuation.startWith(r); // suspend within the try/catch
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // continue without exception and end
                                            // in finally
        assertTrue(c != null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertTrue(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // continue within the finally and end
                                            // in return
        assertTrue(c == null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertTrue(r.e);
        assertTrue(r.f);

    }

    public void testSimpleTryCatchWithException() throws Exception {
        assertTrue(fromSuite());
        final SimpleTryCatch r = new SimpleTryCatch(true);
        Continuation c;

        c = Continuation.startWith(r); // suspend in the try/catch
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // exception jumps into exception
                                            // block and suspends
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertTrue(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // continue in the exception block and
                                            // then suspends in finally
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // continue in finally
        assertTrue(c == null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertTrue(r.f);
    }

    public void testSimpleSynchronized() throws Exception {
        assertTrue(fromSuite());
        final SimpleSynchronized r = new SimpleSynchronized();
        Continuation c;

        c = Continuation.startWith(r); // suspend right away
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // resume and run into synchronized
                                            // block where we suspend again
        assertTrue(c != null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // continue inside the synchronized
                                            // block and suspend after
        assertTrue(c != null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertFalse(r.f);

        c = Continuation.continueWith(c); // resume and then return
        assertTrue(c == null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertTrue(r.f);
    }

    public void testStack() throws Exception {
        assertTrue(fromSuite());
        final Runnable r = new Stack();
        final Continuation c = Continuation.startWith(r);
        assertNull(c); // does not have a suspend
    }

    public void testNestedSynchronized() throws Exception {
        final NestedSynchronized r = new NestedSynchronized();
        Continuation c;

        c = Continuation.startWith(r);
        assertTrue(c != null);
        assertTrue(r.a);
        assertFalse(r.b);
        assertFalse(r.c);
        assertFalse(r.d);
        assertFalse(r.e);
        assertFalse(r.f);
        assertFalse(r.g);
        assertFalse(r.h);
        assertFalse(r.i);
        assertFalse(r.j);

        c = Continuation.continueWith(c);
        assertTrue(c != null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertFalse(r.f);
        assertFalse(r.g);
        assertFalse(r.h);
        assertFalse(r.i);
        assertFalse(r.j);

        c = Continuation.continueWith(c);
        assertTrue(c != null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertTrue(r.f);
        assertTrue(r.g);
        assertTrue(r.h);
        assertTrue(r.i);
        assertFalse(r.j);

        c = Continuation.continueWith(c);
        assertTrue(c == null);
        assertTrue(r.a);
        assertTrue(r.b);
        assertTrue(r.c);
        assertTrue(r.d);
        assertTrue(r.e);
        assertTrue(r.f);
        assertTrue(r.g);
        assertTrue(r.h);
        assertTrue(r.i);
        assertTrue(r.j);
    }

    public void testASMRewriteBug() throws Exception {
        RewriteBugs.calculateCartesianProduct(new String[] { "a", "b" },
                new Object[][] { { "1", "2" }, { "3", "4" } });
    }
}
