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

package Applications.jetty.websocket.common.events.annotated;

import Applications.jetty.util.StringUtil;
import Applications.jetty.websocket.api.InvalidWebSocketException;
import Applications.jetty.websocket.common.events.ParamList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@SuppressWarnings("serial")
public class InvalidSignatureException extends InvalidWebSocketException {
    public static InvalidSignatureException build(Method method, Class<? extends Annotation> annoClass, ParamList... paramlists) {
        // Build big detailed exception to help the developer
        StringBuilder err = new StringBuilder();
        err.append("Invalid declaration of ");
        err.append(method);
        err.append(StringUtil.__LINE_SEPARATOR);

        err.append("Acceptable method declarations for @");
        err.append(annoClass.getSimpleName());
        err.append(" are:");
        for (ParamList validParams : paramlists) {
            for (Class<?>[] params : validParams) {
                err.append(StringUtil.__LINE_SEPARATOR);
                err.append("public void ").append(method.getName());
                err.append('(');
                boolean delim = false;
                for (Class<?> type : params) {
                    if (delim) {
                        err.append(',');
                    }
                    err.append(' ');
                    err.append(type.getName());
                    if (type.isArray()) {
                        err.append("[]");
                    }
                    delim = true;
                }
                err.append(')');
            }
        }
        return new InvalidSignatureException(err.toString());
    }

    public InvalidSignatureException(String message) {
        super(message);
    }

    public InvalidSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
