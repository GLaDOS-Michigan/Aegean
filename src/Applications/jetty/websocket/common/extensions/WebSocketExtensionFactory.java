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

package Applications.jetty.websocket.common.extensions;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.util.StringUtil;
import Applications.jetty.websocket.api.WebSocketException;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.extensions.Extension;
import Applications.jetty.websocket.api.extensions.ExtensionConfig;
import Applications.jetty.websocket.api.extensions.ExtensionFactory;

public class WebSocketExtensionFactory extends ExtensionFactory {
    private WebSocketPolicy policy;
    private ByteBufferPool bufferPool;

    public WebSocketExtensionFactory(WebSocketPolicy policy, ByteBufferPool bufferPool) {
        super();
        this.policy = policy;
        this.bufferPool = bufferPool;
    }

    @Override
    public Extension newInstance(ExtensionConfig config) {
        if (config == null) {
            return null;
        }

        String name = config.getName();
        if (StringUtil.isBlank(name)) {
            return null;
        }

        Class<? extends Extension> extClass = getExtension(name);
        if (extClass == null) {
            return null;
        }

        try {
            Extension ext = extClass.newInstance();
            if (ext instanceof AbstractExtension) {
                AbstractExtension aext = (AbstractExtension) ext;
                aext.setConfig(config);
                aext.setPolicy(policy);
                aext.setBufferPool(bufferPool);
            }
            return ext;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new WebSocketException("Cannot instantiate extension: " + extClass, e);
        }
    }
}
