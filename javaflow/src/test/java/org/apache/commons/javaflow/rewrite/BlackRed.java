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
package org.apache.commons.javaflow.rewrite;

import org.apache.commons.javaflow.Continuation;

import java.io.Serializable;


/**
 * Test for making sure that rstack works correctly. For this test we need to
 * have a stack frame that goes through multiple objects of different types.
 */
@SuppressWarnings("unused")
public final class BlackRed implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;


    public void run() {
        // new Black( new Red( new Black( new Suspend()))).run();
        new Black( new Suspend()).run();
    }


    class Black implements Runnable {
        final Runnable r;

        public Black( Runnable r) {
            this.r = r;
        }

        public void run() {
            String s = "foo"; // have some random variable
            r.run();
        }
    }


    class Red implements Runnable {
        final Runnable r;

        public Red( Runnable r) {
            this.r = r;
        }

        public void run() {
            int i = 5; // have some random variable
            r.run();
        }
    }


    class Suspend implements Runnable {
        public void run() {
            Continuation.suspend();
        }
    }

}

