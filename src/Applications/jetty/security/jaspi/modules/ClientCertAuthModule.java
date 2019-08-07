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

package Applications.jetty.security.jaspi.modules;

import Applications.jetty.util.B64Code;
import Applications.jetty.util.security.Constraint;
import Applications.jetty.util.security.Password;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;

/**
 * @version $Rev: 4530 $ $Date: 2009-02-13 00:47:44 +0100 (Fri, 13 Feb 2009) $
 * @deprecated use *ServerAuthentication
 */
public class ClientCertAuthModule extends BaseAuthModule {

    public ClientCertAuthModule() {
    }

    public ClientCertAuthModule(CallbackHandler callbackHandler) {
        super(callbackHandler);
    }

    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
                                      Subject serviceSubject)
            throws AuthException {
        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        java.security.cert.X509Certificate[] certs = (java.security.cert.X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");

        try {
            // Need certificates.
            if (certs == null || certs.length == 0 || certs[0] == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "A client certificate is required for accessing this web application but the server's listener is not configured for mutual authentication (or the client did not provide a certificate).");
                return AuthStatus.SEND_FAILURE;
            }
            Principal principal = certs[0].getSubjectDN();
            if (principal == null) principal = certs[0].getIssuerDN();
            final String username = principal == null ? "clientcert" : principal.getName();
            // TODO no idea if this is correct
            final String password = new String(B64Code.encode(certs[0].getSignature()));

            // TODO is cert_auth correct?
            if (login(clientSubject, username, new Password(password), Constraint.__CERT_AUTH, messageInfo)) {
                return AuthStatus.SUCCESS;
            }

            if (!isMandatory(messageInfo)) {
                return AuthStatus.SUCCESS;
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The provided client certificate does not correspond to a trusted user.");
            return AuthStatus.SEND_FAILURE;
        } catch (IOException e) {
            throw new AuthException(e.getMessage());
        } catch (UnsupportedCallbackException e) {
            throw new AuthException(e.getMessage());
        }

    }
}
