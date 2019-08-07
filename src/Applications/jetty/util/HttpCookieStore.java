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

package Applications.jetty.util;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link CookieStore} that delegates to an instance created by {@link CookieManager}
 * via {@link CookieManager#getCookieStore()}.
 */
public class HttpCookieStore implements CookieStore {
    private final CookieStore delegate;

    public HttpCookieStore() {
        delegate = new CookieManager().getCookieStore();
    }

    @Override
    public void add(URI uri, HttpCookie cookie) {
        delegate.add(uri, cookie);
    }

    @Override
    public List<HttpCookie> get(URI uri) {
        return delegate.get(uri);
    }

    @Override
    public List<HttpCookie> getCookies() {
        return delegate.getCookies();
    }

    @Override
    public List<URI> getURIs() {
        return delegate.getURIs();
    }

    @Override
    public boolean remove(URI uri, HttpCookie cookie) {
        return delegate.remove(uri, cookie);
    }

    @Override
    public boolean removeAll() {
        return delegate.removeAll();
    }

    public static class Empty implements CookieStore {
        @Override
        public void add(URI uri, HttpCookie cookie) {
        }

        @Override
        public List<HttpCookie> get(URI uri) {
            return Collections.emptyList();
        }

        @Override
        public List<HttpCookie> getCookies() {
            return Collections.emptyList();
        }

        @Override
        public List<URI> getURIs() {
            return Collections.emptyList();
        }

        @Override
        public boolean remove(URI uri, HttpCookie cookie) {
            return false;
        }

        @Override
        public boolean removeAll() {
            return false;
        }
    }
}
