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

package Applications.jetty.client;

import Applications.jetty.client.api.ContentResponse;
import Applications.jetty.client.api.Response;
import Applications.jetty.http.HttpFields;
import Applications.jetty.http.HttpVersion;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

public class HttpContentResponse implements ContentResponse {
    private final Response response;
    private final byte[] content;
    private final String encoding;

    public HttpContentResponse(Response response, byte[] content, String encoding) {
        this.response = response;
        this.content = content;
        this.encoding = encoding;
    }

    @Override
    public long getConversationID() {
        return response.getConversationID();
    }

    @Override
    public <T extends ResponseListener> List<T> getListeners(Class<T> listenerClass) {
        return response.getListeners(listenerClass);
    }

    @Override
    public HttpVersion getVersion() {
        return response.getVersion();
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public String getReason() {
        return response.getReason();
    }

    @Override
    public HttpFields getHeaders() {
        return response.getHeaders();
    }

    @Override
    public boolean abort(Throwable cause) {
        return response.abort(cause);
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public String getContentAsString() {
        String encoding = this.encoding;
        if (encoding == null) {
            return new String(getContent(), StandardCharsets.UTF_8);
        } else {
            try {
                return new String(getContent(), encoding);
            } catch (UnsupportedEncodingException e) {
                throw new UnsupportedCharsetException(encoding);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%s[%s %d %s - %d bytes]",
                HttpContentResponse.class.getSimpleName(),
                getVersion(),
                getStatus(),
                getReason(),
                getContent().length);
    }
}
