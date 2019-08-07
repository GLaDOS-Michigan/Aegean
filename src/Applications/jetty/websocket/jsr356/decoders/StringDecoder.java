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

package Applications.jetty.websocket.jsr356.decoders;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;


/**
 * Default implementation of the {@link Text} Message to {@link String} decoder
 */
public class StringDecoder extends AbstractDecoder implements Decoder.Text<String> {
    public static final StringDecoder INSTANCE = new StringDecoder();

    @Override
    public String decode(String s) throws DecodeException {
        return s;
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }
}
