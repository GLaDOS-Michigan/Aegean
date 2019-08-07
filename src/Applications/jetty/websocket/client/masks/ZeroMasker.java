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

package Applications.jetty.websocket.client.masks;

import Applications.jetty.websocket.common.WebSocketFrame;

import java.util.Arrays;

public class ZeroMasker implements Masker {
    private final byte mask[];

    public ZeroMasker() {
        this.mask = new byte[4];
        Arrays.fill(mask, (byte) 0);
    }

    @Override
    public void setMask(WebSocketFrame frame) {
        frame.setMask(mask);
    }
}