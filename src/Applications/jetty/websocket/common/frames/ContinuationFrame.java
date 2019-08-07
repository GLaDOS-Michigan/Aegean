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

public class ContinuationFrame extends DataFrame {
    public ContinuationFrame() {
        super(OpCode.CONTINUATION);
    }

    public ContinuationFrame setPayload(ByteBuffer buf) {
        super.setPayload(buf);
        return this;
    }

    public ContinuationFrame setPayload(byte buf[]) {
        return this.setPayload(ByteBuffer.wrap(buf));
    }

    public ContinuationFrame setPayload(String message) {
        return this.setPayload(StringUtil.getUtf8Bytes(message));
    }

    @Override
    public Type getType() {
        return Type.CONTINUATION;
    }
}
