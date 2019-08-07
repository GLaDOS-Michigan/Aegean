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

import javax.security.auth.message.MessageInfo;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Almost an implementation of jaspi MessageInfo.
 *
 * @version $Rev: 4660 $ $Date: 2009-02-25 17:29:53 +0100 (Wed, 25 Feb 2009) $
 */
public class JaspiMessageInfo implements MessageInfo {
    public static final String MANDATORY_KEY = "javax.security.auth.message.MessagePolicy.isMandatory";
    public static final String AUTH_METHOD_KEY = "javax.servlet.http.authType";
    private ServletRequest request;
    private ServletResponse response;
    private final MIMap map;

    public JaspiMessageInfo(ServletRequest request, ServletResponse response, boolean isAuthMandatory) {
        this.request = request;
        this.response = response;
        //JASPI 3.8.1
        map = new MIMap(isAuthMandatory);
    }

    public Map getMap() {
        return map;
    }

    public Object getRequestMessage() {
        return request;
    }

    public Object getResponseMessage() {
        return response;
    }

    public void setRequestMessage(Object request) {
        this.request = (ServletRequest) request;
    }

    public void setResponseMessage(Object response) {
        this.response = (ServletResponse) response;
    }

    public String getAuthMethod() {
        return map.getAuthMethod();
    }

    public boolean isAuthMandatory() {
        return map.isAuthMandatory();
    }


    //TODO this has bugs in the view implementations.  Changing them will not affect the hardcoded values.
    private static class MIMap implements Map {
        private final boolean isMandatory;
        private String authMethod;
        private Map delegate;

        private MIMap(boolean mandatory) {
            isMandatory = mandatory;
        }

        public int size() {
            return (isMandatory ? 1 : 0) +
                    (authMethod == null ? 0 : 1) +
                    (delegate == null ? 0 : delegate.size());
        }

        public boolean isEmpty() {
            return !isMandatory && authMethod == null && (delegate == null || delegate.isEmpty());
        }

        public boolean containsKey(Object key) {
            if (MANDATORY_KEY.equals(key)) return isMandatory;
            if (AUTH_METHOD_KEY.equals(key)) return authMethod != null;
            return delegate != null && delegate.containsKey(key);
        }

        public boolean containsValue(Object value) {
            if (isMandatory && "true".equals(value)) return true;
            if (authMethod == value || (authMethod != null && authMethod.equals(value))) return true;
            return delegate != null && delegate.containsValue(value);
        }

        public Object get(Object key) {
            if (MANDATORY_KEY.equals(key)) return isMandatory ? "true" : null;
            if (AUTH_METHOD_KEY.equals(key)) return authMethod;
            if (delegate == null) return null;
            return delegate.get(key);
        }

        public Object put(Object key, Object value) {
            if (MANDATORY_KEY.equals(key)) {
                throw new IllegalArgumentException("Mandatory not mutable");
            }
            if (AUTH_METHOD_KEY.equals(key)) {
                String authMethod = this.authMethod;
                this.authMethod = (String) value;
                if (delegate != null) delegate.put(AUTH_METHOD_KEY, value);
                return authMethod;
            }

            return getDelegate(true).put(key, value);
        }

        public Object remove(Object key) {
            if (MANDATORY_KEY.equals(key)) {
                throw new IllegalArgumentException("Mandatory not mutable");
            }
            if (AUTH_METHOD_KEY.equals(key)) {
                String authMethod = this.authMethod;
                this.authMethod = null;
                if (delegate != null) delegate.remove(AUTH_METHOD_KEY);
                return authMethod;
            }
            if (delegate == null) return null;
            return delegate.remove(key);
        }

        public void putAll(Map map) {
            if (map != null) {
                for (Object o : map.entrySet()) {
                    Map.Entry entry = (Entry) o;
                    put(entry.getKey(), entry.getValue());
                }
            }
        }

        public void clear() {
            authMethod = null;
            delegate = null;
        }

        public Set keySet() {
            return getDelegate(true).keySet();
        }

        public Collection values() {
            return getDelegate(true).values();
        }

        public Set entrySet() {
            return getDelegate(true).entrySet();
        }

        private Map getDelegate(boolean create) {
            if (!create || delegate != null) return delegate;
            if (create) {
                delegate = new HashMap();
                if (isMandatory) delegate.put(MANDATORY_KEY, "true");
                if (authMethod != null) delegate.put(AUTH_METHOD_KEY, authMethod);
            }
            return delegate;
        }

        boolean isAuthMandatory() {
            return isMandatory;
        }

        String getAuthMethod() {
            return authMethod;
        }
    }
}
