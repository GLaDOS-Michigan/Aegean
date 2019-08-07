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
 * Default implementation of the {@link Text} Message to {@link Character} decoder
 */
public class CharacterDecoder extends AbstractDecoder implements Decoder.Text<Character> {
    public static final CharacterDecoder INSTANCE = new CharacterDecoder();

    @Override
    public Character decode(String s) throws DecodeException {
        return Character.valueOf(s.charAt(0));
    }

    @Override
    public boolean willDecode(String s) {
        if (s == null) {
            return false;
        }
        if (s.length() == 1) {
            return true;
        }
        // can only parse 1 character
        return false;
    }
}
