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

package Applications.jetty.websocket.jsr356.metadata;

import Applications.jetty.websocket.api.InvalidWebSocketException;

import javax.websocket.Decoder;

/**
 * Thrown when a duplicate coder is encountered when attempting to identify a Endpoint's metadata ( {@link Decoder} or {@link Encoder})
 */
public class DuplicateCoderException extends InvalidWebSocketException {
    private static final long serialVersionUID = -3049181444035417170L;

    public DuplicateCoderException(String message) {
        super(message);
    }

    public DuplicateCoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
