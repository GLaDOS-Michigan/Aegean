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

package Applications.jetty.websocket.api.annotations;

import Applications.jetty.websocket.api.Session;

import java.lang.annotation.*;

/**
 * (ADVANCED) Annotation for tagging methods to receive frame events.
 * <p>
 * Acceptable method patterns.<br>
 * Note: <code>methodName</code> can be any name you want to use.
 * <ol>
 * <li><code>public void methodName({@link Frame} frame)</code></li>
 * <li><code>public void methodName({@link Session} session, {@link Frame} frame)</code></li>
 * </ol>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value =
        {ElementType.METHOD})
public @interface OnWebSocketFrame {
    /* no config */
}
