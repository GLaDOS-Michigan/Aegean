//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.websocket.common.frames;

import Applications.jetty.util.StringUtil;
import Applications.jetty.websocket.common.OpCode;

import java.nio.ByteBuffer;

public class BinaryFrame extends DataFrame {
    public BinaryFrame() {
        super(OpCode.BINARY);
    }

    public BinaryFrame setPayload(ByteBuffer buf) {
        super.setPayload(buf);
        return this;
    }

    public BinaryFrame setPayload(byte[] buf) {
        setPayload(ByteBuffer.wrap(buf));
        return this;
    }

    public BinaryFrame setPayload(String payload) {
        setPayload(StringUtil.getUtf8Bytes(payload));
        return this;
    }

    @Override
    public Type getType() {
        return Type.BINARY;
    }
}
