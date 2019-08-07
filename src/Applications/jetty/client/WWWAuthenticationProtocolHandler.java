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

import Applications.jetty.client.api.Request;
import Applications.jetty.client.api.Response;
import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpStatus;

import java.net.URI;

public class WWWAuthenticationProtocolHandler extends AuthenticationProtocolHandler {
    public WWWAuthenticationProtocolHandler(HttpClient client) {
        this(client, DEFAULT_MAX_CONTENT_LENGTH);
    }

    public WWWAuthenticationProtocolHandler(HttpClient client, int maxContentLength) {
        super(client, maxContentLength);
    }

    @Override
    public boolean accept(Request request, Response response) {
        return response.getStatus() == HttpStatus.UNAUTHORIZED_401;
    }

    @Override
    protected HttpHeader getAuthenticateHeader() {
        return HttpHeader.WWW_AUTHENTICATE;
    }

    @Override
    protected HttpHeader getAuthorizationHeader() {
        return HttpHeader.AUTHORIZATION;
    }

    @Override
    protected URI getAuthenticationURI(Request request) {
        return request.getURI();
    }
}
