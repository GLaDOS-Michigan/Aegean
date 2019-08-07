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

import Applications.jetty.security.authentication.LoginCallbackImpl;
import Applications.jetty.security.jaspi.JaspiMessageInfo;
import Applications.jetty.security.jaspi.callback.CredentialValidationCallback;
import Applications.jetty.util.B64Code;
import Applications.jetty.util.security.Credential;
import Applications.jetty.util.security.Password;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * @version $Rev: 4792 $ $Date: 2009-03-18 22:55:52 +0100 (Wed, 18 Mar 2009) $
 * @deprecated use *ServerAuthentication
 */
public class BaseAuthModule implements ServerAuthModule, ServerAuthContext {
    private static final Class[] SUPPORTED_MESSAGE_TYPES = new Class[]{HttpServletRequest.class, HttpServletResponse.class};

    protected static final String LOGIN_SERVICE_KEY = "org.eclipse.jetty.security.jaspi.modules.LoginService";

    protected CallbackHandler callbackHandler;

    public Class[] getSupportedMessageTypes() {
        return SUPPORTED_MESSAGE_TYPES;
    }

    public BaseAuthModule() {
    }

    public BaseAuthModule(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    public void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler, Map options) throws AuthException {
        this.callbackHandler = handler;
    }

    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
        // TODO apparently we either get the LoginCallback or the LoginService
        // but not both :-(
        // Set<LoginCallback> loginCallbacks =
        // subject.getPrivateCredentials(LoginCallback.class);
        // if (!loginCallbacks.isEmpty()) {
        // LoginCallback loginCallback = loginCallbacks.iterator().next();
        // }
        // try {
        // loginService.logout(subject);
        // } catch (ServerAuthException e) {
        // throw new AuthException(e.getMessage());
        // }
    }

    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) throws AuthException {
        // servlets do not need secured responses
        return AuthStatus.SEND_SUCCESS;
    }

    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject) throws AuthException {
        return AuthStatus.SEND_FAILURE;
    }

    /**
     * @param messageInfo message info to examine for mandatory flag
     * @return whether authentication is mandatory or optional
     */
    protected boolean isMandatory(MessageInfo messageInfo) {
        String mandatory = (String) messageInfo.getMap().get(JaspiMessageInfo.MANDATORY_KEY);
        if (mandatory == null) return false;
        return Boolean.valueOf(mandatory);
    }

    protected boolean login(Subject clientSubject, String credentials,
                            String authMethod, MessageInfo messageInfo)
            throws IOException, UnsupportedCallbackException {
        credentials = credentials.substring(credentials.indexOf(' ') + 1);
        credentials = B64Code.decode(credentials, StandardCharsets.ISO_8859_1);
        int i = credentials.indexOf(':');
        String userName = credentials.substring(0, i);
        String password = credentials.substring(i + 1);
        return login(clientSubject, userName, new Password(password), authMethod, messageInfo);
    }

    protected boolean login(Subject clientSubject, String username,
                            Credential credential, String authMethod,
                            MessageInfo messageInfo)
            throws IOException, UnsupportedCallbackException {
        CredentialValidationCallback credValidationCallback = new CredentialValidationCallback(clientSubject, username, credential);
        callbackHandler.handle(new Callback[]{credValidationCallback});
        if (credValidationCallback.getResult()) {
            Set<LoginCallbackImpl> loginCallbacks = clientSubject.getPrivateCredentials(LoginCallbackImpl.class);
            if (!loginCallbacks.isEmpty()) {
                LoginCallbackImpl loginCallback = loginCallbacks.iterator().next();
                CallerPrincipalCallback callerPrincipalCallback = new CallerPrincipalCallback(clientSubject, loginCallback.getUserPrincipal());
                GroupPrincipalCallback groupPrincipalCallback = new GroupPrincipalCallback(clientSubject, loginCallback.getRoles());
                callbackHandler.handle(new Callback[]{callerPrincipalCallback, groupPrincipalCallback});
            }
            messageInfo.getMap().put(JaspiMessageInfo.AUTH_METHOD_KEY, authMethod);
        }
        return credValidationCallback.getResult();

    }
}
