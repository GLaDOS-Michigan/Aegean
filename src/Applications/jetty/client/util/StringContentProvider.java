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

package Applications.jetty.client.util;

import Applications.jetty.client.api.ContentProvider;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A {@link ContentProvider} for strings.
 * <p/>
 * It is possible to specify, at the constructor, an encoding used to convert
 * the string into bytes, by default UTF-8.
 */
public class StringContentProvider extends BytesContentProvider {
    public StringContentProvider(String content) {
        this(content, StandardCharsets.UTF_8);
    }

    public StringContentProvider(String content, String encoding) {
        this(content, Charset.forName(encoding));
    }

    public StringContentProvider(String content, Charset charset) {
        super(content.getBytes(charset));
    }
}
