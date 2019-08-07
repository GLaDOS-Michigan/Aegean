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

package Applications.jetty.websocket.jsr356.encoders;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.nio.ByteBuffer;

public class ByteArrayEncoder implements Encoder.Binary<byte[]> {
    @Override
    public void destroy() {
        /* do nothing */
    }

    @Override
    public ByteBuffer encode(byte[] object) throws EncodeException {
        return ByteBuffer.wrap(object);
    }

    @Override
    public void init(EndpointConfig config) {
        /* do nothing */
    }
}
