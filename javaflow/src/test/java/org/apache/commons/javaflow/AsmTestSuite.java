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
package org.apache.commons.javaflow;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.helper.ClassTransformerClassLoader;
import org.apache.commons.javaflow.rewrite.BlackRed;
import org.apache.commons.javaflow.rewrite.ClassAccess1;
import org.apache.commons.javaflow.rewrite.ClassAccess2;
import org.apache.commons.javaflow.rewrite.ClassAccess3;
import org.apache.commons.javaflow.rewrite.ConstructorInvocation;
import org.apache.commons.javaflow.rewrite.CounterFlow;
import org.apache.commons.javaflow.rewrite.DefaultConstructor;
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
import org.apache.commons.javaflow.suite.SerializationTestCase;
import org.apache.commons.javaflow.suite.VerificationTestCase;

public final class AsmTestSuite extends TestSuite {

    @SuppressWarnings("unchecked")
    public static Test suite() throws Exception {

        // LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", SimpleLog.class.getName());
        // System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "debug");
        
        final ClassTransformerClassLoader classloader =
            new ClassTransformerClassLoader(
                new AsmClassTransformer(),
                new Class[] { // instrument
                    BlackRed.class,
                    ClassAccess1.class,
                    ClassAccess2.class,
                    ClassAccess3.class,
                    CounterFlow.class,
                    ConstructorInvocation.class,
                    DefaultConstructor.class,
                    NestedSynchronized.class,
                    NewObject.class,
                    NoReference.class,
                    NullVariableMethodFlow.class,
                    NullLocalVariable.class,
                    RewriteBugs.class,
                    Simple.class,
                    SimpleSerializable.class,
                    SimpleSynchronized.class,
                    SimpleTryCatch.class,
                    Stack.class,
                    },  
                new Class[] { // load
                    VerificationTestCase.class,
                    SerializationTestCase.class
                    }  
                );
        
        final TestSuite suite = new TestSuite();
        suite.setName("ASM");
        suite.addTestSuite((Class<? extends TestCase>)classloader.loadClass(VerificationTestCase.class.getName()));
        suite.addTestSuite((Class<? extends TestCase>)classloader.loadClass(SerializationTestCase.class.getName()));
        return suite;
    }
}
