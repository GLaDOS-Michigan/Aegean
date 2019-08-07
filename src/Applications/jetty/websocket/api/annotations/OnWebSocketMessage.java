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

import java.io.Reader;
import java.lang.annotation.*;

/**
 * Annotation for tagging methods to receive Binary or Text Message events.
 * <p>
 * Acceptable method patterns.<br>
 * Note: <code>methodName</code> can be any name you want to use.
 * <p>
 * <u>Text Message Versions</u>
 * <ol>
 * <li><code>public void methodName(String text)</code></li>
 * <li><code>public void methodName({@link Session} session, String text)</code></li>
 * <li><code>public void methodName(Reader reader)</code></li>
 * <li><code>public void methodName({@link Session} session, Reader reader)</code></li>
 * </ol>
 * Note: that the {@link Reader} in this case will always use UTF-8 encoding/charset (this is dictated by the RFC 6455 spec for Text Messages. If you need to
 * use a non-UTF-8 encoding/charset, you are instructed to use the binary messaging techniques.
 * <p>
 * <u>Binary Message Versions</u>
 * <ol>
 * <li><code>public void methodName(byte buf[], int offset, int length)</code></li>
 * <li><code>public void methodName({@link Session} session, byte buf[], int offset, int length)</code></li>
 * <li><code>public void methodName(InputStream stream)</code></li>
 * <li><code>public void methodName({@link Session} session, InputStream stream)</code></li>
 * </ol>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value =
        {ElementType.METHOD})
public @interface OnWebSocketMessage {
    /* no config */
}
