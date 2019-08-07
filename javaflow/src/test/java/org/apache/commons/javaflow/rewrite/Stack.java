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

/**
 * Regression test case.
 *
 * <p>
 * When the stack size reaches the maximum in a constructor method invocation,
 * there was a bug where we failed to expand the stack size appropriately.
 *
 * This is a regression test for that case.
 */
@SuppressWarnings("unused")
public final class Stack implements Runnable {
    public void run() {
        final Object o = foo("abc","def");
    }

    private Object foo(String a, String b) {
        return new StrStr(a,b);
    }

    private static final class StrStr {
        private final String value;

        public StrStr(String a, String b) {
            value = a+b;
        }

        public String toString() {
            return value;
        }
    }
}
