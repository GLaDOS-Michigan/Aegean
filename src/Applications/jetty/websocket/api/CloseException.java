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

@SuppressWarnings("serial")
public class CloseException extends WebSocketException {
    private int statusCode;

    public CloseException(int closeCode, String message) {
        super(message);
        this.statusCode = closeCode;
    }

    public CloseException(int closeCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = closeCode;
    }

    public CloseException(int closeCode, Throwable cause) {
        super(cause);
        this.statusCode = closeCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

}
