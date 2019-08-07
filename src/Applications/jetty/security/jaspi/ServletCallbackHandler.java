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

package Applications.jetty.security.jaspi;

import Applications.jetty.security.LoginService;
import Applications.jetty.security.authentication.LoginCallback;
import Applications.jetty.security.authentication.LoginCallbackImpl;
import Applications.jetty.security.jaspi.callback.CredentialValidationCallback;
import Applications.jetty.server.UserIdentity;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.*;
import java.io.IOException;

/**
 * Idiot class required by jaspi stupidity
 *
 * @version $Rev: 4793 $ $Date: 2009-03-19 00:00:01 +0100 (Thu, 19 Mar 2009) $
 */
public class ServletCallbackHandler implements CallbackHandler {
    private final LoginService _loginService;

    private final ThreadLocal<CallerPrincipalCallback> _callerPrincipals = new ThreadLocal<CallerPrincipalCallback>();
    private final ThreadLocal<GroupPrincipalCallback> _groupPrincipals = new ThreadLocal<GroupPrincipalCallback>();

    public ServletCallbackHandler(LoginService loginService) {
        _loginService = loginService;
    }

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            // jaspi to server communication
            if (callback instanceof CallerPrincipalCallback) {
                _callerPrincipals.set((CallerPrincipalCallback) callback);
            } else if (callback instanceof GroupPrincipalCallback) {
                _groupPrincipals.set((GroupPrincipalCallback) callback);
            } else if (callback instanceof PasswordValidationCallback) {
                PasswordValidationCallback passwordValidationCallback = (PasswordValidationCallback) callback;
                Subject subject = passwordValidationCallback.getSubject();

                UserIdentity user = _loginService.login(passwordValidationCallback.getUsername(), passwordValidationCallback.getPassword());

                if (user != null) {
                    passwordValidationCallback.setResult(true);
                    passwordValidationCallback.getSubject().getPrincipals().addAll(user.getSubject().getPrincipals());
                    passwordValidationCallback.getSubject().getPrivateCredentials().add(user);
                }
            } else if (callback instanceof CredentialValidationCallback) {
                CredentialValidationCallback credentialValidationCallback = (CredentialValidationCallback) callback;
                Subject subject = credentialValidationCallback.getSubject();
                LoginCallback loginCallback = new LoginCallbackImpl(subject,
                        credentialValidationCallback.getUsername(),
                        credentialValidationCallback.getCredential());

                UserIdentity user = _loginService.login(credentialValidationCallback.getUsername(), credentialValidationCallback.getCredential());

                if (user != null) {
                    loginCallback.setUserPrincipal(user.getUserPrincipal());
                    credentialValidationCallback.getSubject().getPrivateCredentials().add(loginCallback);
                    credentialValidationCallback.setResult(true);
                    credentialValidationCallback.getSubject().getPrincipals().addAll(user.getSubject().getPrincipals());
                    credentialValidationCallback.getSubject().getPrivateCredentials().add(user);
                }
            }
            // server to jaspi communication
            // TODO implement these
            else if (callback instanceof CertStoreCallback) {
            } else if (callback instanceof PrivateKeyCallback) {
            } else if (callback instanceof SecretKeyCallback) {
            } else if (callback instanceof TrustStoreCallback) {
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    public CallerPrincipalCallback getThreadCallerPrincipalCallback() {
        CallerPrincipalCallback callerPrincipalCallback = _callerPrincipals.get();
        _callerPrincipals.remove();
        return callerPrincipalCallback;
    }

    public GroupPrincipalCallback getThreadGroupPrincipalCallback() {
        GroupPrincipalCallback groupPrincipalCallback = _groupPrincipals.get();
        _groupPrincipals.remove();
        return groupPrincipalCallback;
    }
}
