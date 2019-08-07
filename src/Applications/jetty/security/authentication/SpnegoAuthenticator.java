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

package Applications.jetty.security.authentication;

import Applications.jetty.http.HttpHeader;
import Applications.jetty.security.ServerAuthException;
import Applications.jetty.security.UserAuthentication;
import Applications.jetty.server.Authentication;
import Applications.jetty.server.Authentication.User;
import Applications.jetty.server.UserIdentity;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.security.Constraint;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SpnegoAuthenticator extends LoginAuthenticator {
    private static final Logger LOG = Log.getLogger(SpnegoAuthenticator.class);
    private String _authMethod = Constraint.__SPNEGO_AUTH;

    public SpnegoAuthenticator() {
    }

    /**
     * Allow for a custom authMethod value to be set for instances where SPENGO may not be appropriate
     *
     * @param authMethod
     */
    public SpnegoAuthenticator(String authMethod) {
        _authMethod = authMethod;
    }

    @Override
    public String getAuthMethod() {
        return _authMethod;
    }

    @Override
    public Authentication validateRequest(ServletRequest request, ServletResponse response, boolean mandatory) throws ServerAuthException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String header = req.getHeader(HttpHeader.AUTHORIZATION.asString());

        if (!mandatory) {
            return new DeferredAuthentication(this);
        }

        // check to see if we have authorization headers required to continue
        if (header == null) {
            try {
                if (DeferredAuthentication.isDeferred(res)) {
                    return Authentication.UNAUTHENTICATED;
                }

                LOG.debug("SpengoAuthenticator: sending challenge");
                res.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), HttpHeader.NEGOTIATE.asString());
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return Authentication.SEND_CONTINUE;
            } catch (IOException ioe) {
                throw new ServerAuthException(ioe);
            }
        } else if (header != null && header.startsWith(HttpHeader.NEGOTIATE.asString())) {
            String spnegoToken = header.substring(10);

            UserIdentity user = login(null, spnegoToken, request);

            if (user != null) {
                return new UserAuthentication(getAuthMethod(), user);
            }
        }

        return Authentication.UNAUTHENTICATED;
    }

    @Override
    public boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, User validatedUser) throws ServerAuthException {
        return true;
    }

}
