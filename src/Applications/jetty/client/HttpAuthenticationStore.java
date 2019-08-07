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

import Applications.jetty.client.api.Authentication;
import Applications.jetty.client.api.AuthenticationStore;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpAuthenticationStore implements AuthenticationStore {
    private final List<Authentication> authentications = new CopyOnWriteArrayList<>();
    private final Map<URI, Authentication.Result> results = new ConcurrentHashMap<>();

    @Override
    public void addAuthentication(Authentication authentication) {
        authentications.add(authentication);
    }

    @Override
    public void removeAuthentication(Authentication authentication) {
        authentications.remove(authentication);
    }

    @Override
    public void clearAuthentications() {
        authentications.clear();
    }

    @Override
    public Authentication findAuthentication(String type, URI uri, String realm) {
        for (Authentication authentication : authentications) {
            if (authentication.matches(type, uri, realm))
                return authentication;
        }
        return null;
    }

    @Override
    public void addAuthenticationResult(Authentication.Result result) {
        results.put(result.getURI(), result);
    }

    @Override
    public void removeAuthenticationResult(Authentication.Result result) {
        results.remove(result.getURI());
    }

    @Override
    public void clearAuthenticationResults() {
        results.clear();
    }

    @Override
    public Authentication.Result findAuthenticationResult(URI uri) {
        // TODO: I should match the longest URI
        for (Map.Entry<URI, Authentication.Result> entry : results.entrySet()) {
            if (uri.toString().startsWith(entry.getKey().toString()))
                return entry.getValue();
        }
        return null;
    }
}
