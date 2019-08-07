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

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.commons.javaflow.Continuation;

public class ConstructorInvocation implements Runnable {

    final Object d = new java.util.Date();

    public void run() {
        // No problem for fields
        System.out.println(d);

        // No problem for primitives
        for (int i = 0; i < 3; i++)
            System.out.println(i);

        // No problem for "externalized" constructor call
        final Map<String, String> map1 = newMap();

        // But direct constructor invocation always causes an error
        final Map<String, String> map2 = new HashMap<String, String>();

        map1.put("A", "B");
        System.out.println(map1);

        map2.put("B", "C");
        System.out.println(map2);

        Continuation.suspend();
        System.out.println("RESUMED, YES!");

        Assert.assertEquals("B", map1.get("A"));
        Assert.assertEquals("C", map2.get("B"));
    }

    protected Map<String, String> newMap() {
        return new HashMap<String, String>();
    }
}
