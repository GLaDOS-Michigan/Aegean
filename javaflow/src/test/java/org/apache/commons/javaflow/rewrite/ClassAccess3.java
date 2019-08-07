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

public final class ClassAccess3 implements Runnable {

    /*
       L0 (0)
        GETSTATIC ClassAccess2.class$0 : Class
        DUP
        IFNONNULL L1
        POP
       
       L2 (5)
        LDC "asm.data.ClassAccess2"
        INVOKESTATIC Class.forName(String) : Class
       
       L3 (8)
        DUP
        PUTSTATIC ClassAccess2.class$0 : Class
        GOTO L1
       
       L4 (12)
        NEW NoClassDefFoundError
        DUP_X1
        SWAP
        INVOKEVIRTUAL Throwable.getMessage() : String
        INVOKESPECIAL NoClassDefFoundError.<init>(String) : void
        ATHROW
       
       L1 (19)
        ASTORE 1: clazz1
       L5 (21)
        INVOKESTATIC Continuation.suspend() : void
       L6 (23)
        RETURN

       L7 (25)
        TRYCATCHBLOCK L2 L3 L4 ClassNotFoundException
     */
  
    @SuppressWarnings("rawtypes")
    static Class class$0;
  
    public void run() {
        // final Class clazz1 = ClassAccess2.class;
        // final Class clazz2 = this.getClass();
        // if(class$0==null) {
          try {
            class$0 = Class.forName("org.apache.commons.javaflow.rewrite.ClassAccess2");
          } catch(ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
          }
        // }
        
        Continuation.suspend();
    }

}