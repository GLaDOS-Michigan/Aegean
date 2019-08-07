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

import org.apache.commons.javaflow.bytecode.StackRecorder;

/**
 * This exception is used to signal
 * a control flow change that needs
 * the cooperation inside {@link StackRecorder}.
 *
 * <p>
 * This class is only for javaflow internal code.
 */
public final class ContinuationDeath extends Error {

    private static final long serialVersionUID = 1L;

    final String mode;

    public ContinuationDeath(String mode) {
        this.mode = mode;
    }

    /**
     * Signals that the continuation wants to exit the execution.
     */
    static final String MODE_EXIT = "exit";
    /**
     * Signals that the execution should restart immediately
     * from where it resumed.
     */
    static final String MODE_AGAIN = "again";
    /**
     * Signals that the exeuction should suspend,
     * by using the original continuation.
     */
    static final String MODE_CANCEL = "cancel";
}
