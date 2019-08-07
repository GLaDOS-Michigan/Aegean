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

import junit.framework.Assert;

/**
 * Test that allocates a lot of new objects. Javaflow performs some tricky
 * instrumentation on new object allocations, especially when it has arguments.
 * Nesting object allocations makes it even more interesting.
 */
public final class NewObject implements Runnable {
  static char[] ch = { 'a', 'b', 'c'};
  
  public void run() {

    String s = new String( new String( new String( ch, 0, ch.length).toCharArray(), 0, ch.length));
    // String s = new String( new String( ch).toCharArray());

    Assert.assertEquals( s, "abc");
  }

}

