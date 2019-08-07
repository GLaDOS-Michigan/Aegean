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

package Applications.jetty.websocket.api;

/**
 * Per spec, a protocol error should result in a Close frame of status code 1002 (PROTOCOL_ERROR)
 */
@SuppressWarnings("serial")
public class ProtocolException extends CloseException {
    public ProtocolException(String message) {
        super(StatusCode.PROTOCOL, message);
    }

    public ProtocolException(String message, Throwable t) {
        super(StatusCode.PROTOCOL, message, t);
    }

    public ProtocolException(Throwable t) {
        super(StatusCode.PROTOCOL, t);
    }
}
