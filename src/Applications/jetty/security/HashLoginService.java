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

import Applications.jetty.security.PropertyUserStore.UserListener;
import Applications.jetty.server.UserIdentity;
import Applications.jetty.util.Scanner;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;
import Applications.jetty.util.security.Credential;

import java.io.IOException;

/* ------------------------------------------------------------ */

/**
 * Properties User Realm.
 * <p>
 * An implementation of UserRealm that stores users and roles in-memory in HashMaps.
 * <p>
 * Typically these maps are populated by calling the load() method or passing a properties resource to the constructor. The format of the properties file is:
 * <p>
 * <PRE>
 * username: password [,rolename ...]
 * </PRE>
 * <p>
 * Passwords may be clear text, obfuscated or checksummed. The class com.eclipse.Util.Password should be used to generate obfuscated passwords or password
 * checksums.
 * <p>
 * If DIGEST Authentication is used, the password must be in a recoverable format, either plain text or OBF:.
 */
public class HashLoginService extends MappedLoginService implements UserListener {
    private static final Logger LOG = Log.getLogger(HashLoginService.class);

    private PropertyUserStore _propertyUserStore;
    private String _config;
    private Resource _configResource;
    private Scanner _scanner;
    private int _refreshInterval = 0;// default is not to reload

    /* ------------------------------------------------------------ */
    public HashLoginService() {
    }

    /* ------------------------------------------------------------ */
    public HashLoginService(String name) {
        setName(name);
    }

    /* ------------------------------------------------------------ */
    public HashLoginService(String name, String config) {
        setName(name);
        setConfig(config);
    }

    /* ------------------------------------------------------------ */
    public String getConfig() {
        return _config;
    }

    /* ------------------------------------------------------------ */
    public void getConfig(String config) {
        _config = config;
    }

    /* ------------------------------------------------------------ */
    public Resource getConfigResource() {
        return _configResource;
    }

    /* ------------------------------------------------------------ */

    /**
     * Load realm users from properties file. The property file maps usernames to password specs followed by an optional comma separated list of role names.
     *
     * @param config Filename or url of user properties file.
     */
    public void setConfig(String config) {
        _config = config;
    }

    /* ------------------------------------------------------------ */
    public void setRefreshInterval(int msec) {
        _refreshInterval = msec;
    }

    /* ------------------------------------------------------------ */
    public int getRefreshInterval() {
        return _refreshInterval;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected UserIdentity loadUser(String username) {
        return null;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void loadUsers() throws IOException {
        // TODO: Consider refactoring MappedLoginService to not have to override with unused methods
    }

    /* ------------------------------------------------------------ */

    /**
     * @see Applications.jetty.util.component.AbstractLifeCycle#doStart()
     */
    protected void doStart() throws Exception {
        super.doStart();

        if (_propertyUserStore == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("doStart: Starting new PropertyUserStore. PropertiesFile: " + _config + " refreshInterval: " + _refreshInterval);

            _propertyUserStore = new PropertyUserStore();
            _propertyUserStore.setRefreshInterval(_refreshInterval);
            _propertyUserStore.setConfig(_config);
            _propertyUserStore.registerUserListener(this);
            _propertyUserStore.start();
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @see Applications.jetty.util.component.AbstractLifeCycle#doStop()
     */
    protected void doStop() throws Exception {
        super.doStop();
        if (_scanner != null)
            _scanner.stop();
        _scanner = null;
    }

    /* ------------------------------------------------------------ */
    public void update(String userName, Credential credential, String[] roleArray) {
        if (LOG.isDebugEnabled())
            LOG.debug("update: " + userName + " Roles: " + roleArray.length);
        putUser(userName, credential, roleArray);
    }

    /* ------------------------------------------------------------ */
    public void remove(String userName) {
        if (LOG.isDebugEnabled())
            LOG.debug("remove: " + userName);
        removeUser(userName);
    }
}
