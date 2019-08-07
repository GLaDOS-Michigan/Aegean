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

package Applications.jetty.security;

import Applications.jetty.server.Authentication.User;
import Applications.jetty.server.UserIdentity;
import Applications.jetty.server.UserIdentity.Scope;

import java.util.Set;

/**
 * AbstractUserAuthentication
 * <p>
 * <p>
 * Base class for representing an authenticated user.
 */
public abstract class AbstractUserAuthentication implements User {
    protected String _method;
    protected UserIdentity _userIdentity;


    public AbstractUserAuthentication(String method, UserIdentity userIdentity) {
        _method = method;
        _userIdentity = userIdentity;
    }


    @Override
    public String getAuthMethod() {
        return _method;
    }

    @Override
    public UserIdentity getUserIdentity() {
        return _userIdentity;
    }

    @Override
    public boolean isUserInRole(Scope scope, String role) {
        String roleToTest = null;
        if (scope != null && scope.getRoleRefMap() != null)
            roleToTest = scope.getRoleRefMap().get(role);
        if (roleToTest == null)
            roleToTest = role;
        //Servlet Spec 3.1 pg 125 if testing special role **
        if ("**".equals(roleToTest.trim())) {
            //if ** is NOT a declared role name, the we return true 
            //as the user is authenticated. If ** HAS been declared as a
            //role name, then we have to check if the user has that role
            if (!declaredRolesContains("**"))
                return true;
            else
                return _userIdentity.isUserInRole(role, scope);
        }

        return _userIdentity.isUserInRole(role, scope);
    }

    public boolean declaredRolesContains(String roleName) {
        SecurityHandler security = SecurityHandler.getCurrentSecurityHandler();
        if (security == null)
            return false;

        if (security instanceof ConstraintAware) {
            Set<String> declaredRoles = ((ConstraintAware) security).getRoles();
            return (declaredRoles != null) && declaredRoles.contains(roleName);
        }

        return false;
    }
}
