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

package Applications.jetty.server.session;

import Applications.jetty.server.SessionIdManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.ref.WeakReference;
import java.util.*;

/* ------------------------------------------------------------ */

/**
 * HashSessionIdManager. An in-memory implementation of the session ID manager.
 */
public class HashSessionIdManager extends AbstractSessionIdManager {
    private final Map<String, Set<WeakReference<HttpSession>>> _sessions = new HashMap<String, Set<WeakReference<HttpSession>>>();

    /* ------------------------------------------------------------ */
    public HashSessionIdManager() {
    }

    /* ------------------------------------------------------------ */
    public HashSessionIdManager(Random random) {
        super(random);
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Collection of String session IDs
     */
    public Collection<String> getSessions() {
        return Collections.unmodifiableCollection(_sessions.keySet());
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Collection of Sessions for the passed session ID
     */
    public Collection<HttpSession> getSession(String id) {
        ArrayList<HttpSession> sessions = new ArrayList<HttpSession>();
        Set<WeakReference<HttpSession>> refs = _sessions.get(id);
        if (refs != null) {
            for (WeakReference<HttpSession> ref : refs) {
                HttpSession session = ref.get();
                if (session != null)
                    sessions.add(session);
            }
        }
        return sessions;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStop() throws Exception {
        _sessions.clear();
        super.doStop();
    }

    /* ------------------------------------------------------------ */

    /**
     * @see SessionIdManager#idInUse(String)
     */
    @Override
    public boolean idInUse(String id) {
        synchronized (this) {
            return _sessions.containsKey(id);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @see SessionIdManager#addSession(HttpSession)
     */
    @Override
    public void addSession(HttpSession session) {
        String id = getClusterId(session.getId());
        WeakReference<HttpSession> ref = new WeakReference<HttpSession>(session);

        synchronized (this) {
            Set<WeakReference<HttpSession>> sessions = _sessions.get(id);
            if (sessions == null) {
                sessions = new HashSet<WeakReference<HttpSession>>();
                _sessions.put(id, sessions);
            }
            sessions.add(ref);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @see SessionIdManager#removeSession(HttpSession)
     */
    @Override
    public void removeSession(HttpSession session) {
        String id = getClusterId(session.getId());

        synchronized (this) {
            Collection<WeakReference<HttpSession>> sessions = _sessions.get(id);
            if (sessions != null) {
                for (Iterator<WeakReference<HttpSession>> iter = sessions.iterator(); iter.hasNext(); ) {
                    WeakReference<HttpSession> ref = iter.next();
                    HttpSession s = ref.get();
                    if (s == null) {
                        iter.remove();
                        continue;
                    }
                    if (s == session) {
                        iter.remove();
                        break;
                    }
                }
                if (sessions.isEmpty())
                    _sessions.remove(id);
            }
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @see SessionIdManager#invalidateAll(String)
     */
    @Override
    public void invalidateAll(String id) {
        Collection<WeakReference<HttpSession>> sessions;
        synchronized (this) {
            sessions = _sessions.remove(id);
        }

        if (sessions != null) {
            for (WeakReference<HttpSession> ref : sessions) {
                AbstractSession session = (AbstractSession) ref.get();
                if (session != null && session.isValid())
                    session.invalidate();
            }
            sessions.clear();
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public void renewSessionId(String oldClusterId, String oldNodeId, HttpServletRequest request) {
        //generate a new id
        String newClusterId = newSessionId(request.hashCode());


        synchronized (this) {
            Set<WeakReference<HttpSession>> sessions = _sessions.remove(oldClusterId); //get the list of sessions with same id from other contexts
            if (sessions != null) {
                for (Iterator<WeakReference<HttpSession>> iter = sessions.iterator(); iter.hasNext(); ) {
                    WeakReference<HttpSession> ref = iter.next();
                    HttpSession s = ref.get();
                    if (s == null) {
                        continue;
                    } else {
                        if (s instanceof AbstractSession) {
                            AbstractSession abstractSession = (AbstractSession) s;
                            abstractSession.getSessionManager().renewSessionId(oldClusterId, oldNodeId, newClusterId, getNodeId(newClusterId, request));
                        }
                    }
                }
                _sessions.put(newClusterId, sessions);
            }
        }
    }

}
