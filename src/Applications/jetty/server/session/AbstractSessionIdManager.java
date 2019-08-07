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
import Applications.jetty.util.component.AbstractLifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.Random;

public abstract class AbstractSessionIdManager extends AbstractLifeCycle implements SessionIdManager {
    private static final Logger LOG = Log.getLogger(AbstractSessionIdManager.class);

    private final static String __NEW_SESSION_ID = "org.eclipse.jetty.server.newSessionId";

    protected Random _random;
    protected boolean _weakRandom;
    protected String _workerName;
    protected String _workerAttr;
    protected long _reseed = 100000L;

    /* ------------------------------------------------------------ */
    public AbstractSessionIdManager() {
    }

    /* ------------------------------------------------------------ */
    public AbstractSessionIdManager(Random random) {
        _random = random;
    }


    /* ------------------------------------------------------------ */

    /**
     * Get the workname. If set, the workername is dot appended to the session
     * ID and can be used to assist session affinity in a load balancer.
     *
     * @return String or null
     */
    @Override
    public String getWorkerName() {
        return _workerName;
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the workname. If set, the workername is dot appended to the session
     * ID and can be used to assist session affinity in a load balancer.
     * A worker name starting with $ is used as a request attribute name to
     * lookup the worker name that can be dynamically set by a request
     * customiser.
     *
     * @param workerName
     */
    public void setWorkerName(String workerName) {
        if (isRunning())
            throw new IllegalStateException(getState());
        if (workerName.contains("."))
            throw new IllegalArgumentException("Name cannot contain '.'");
        _workerName = workerName;
    }

    /* ------------------------------------------------------------ */
    public Random getRandom() {
        return _random;
    }

    /* ------------------------------------------------------------ */
    public synchronized void setRandom(Random random) {
        _random = random;
        _weakRandom = false;
    }

    /* ------------------------------------------------------------ */

    /**
     * @return the reseed probability
     */
    public long getReseed() {
        return _reseed;
    }

    /* ------------------------------------------------------------ */

    /**
     * Set the reseed probability.
     *
     * @param reseed If non zero then when a random long modulo the reseed value == 1, the {@link SecureRandom} will be reseeded.
     */
    public void setReseed(long reseed) {
        _reseed = reseed;
    }

    /* ------------------------------------------------------------ */

    /**
     * Create a new session id if necessary.
     *
     * @see Applications.jetty.server.SessionIdManager#newSessionId(javax.servlet.http.HttpServletRequest, long)
     */
    @Override
    public String newSessionId(HttpServletRequest request, long created) {
        synchronized (this) {
            if (request == null)
                return newSessionId(created);

            // A requested session ID can only be used if it is in use already.
            String requested_id = request.getRequestedSessionId();
            if (requested_id != null) {
                String cluster_id = getClusterId(requested_id);
                if (idInUse(cluster_id))
                    return cluster_id;
            }

            // Else reuse any new session ID already defined for this request.
            String new_id = (String) request.getAttribute(__NEW_SESSION_ID);
            if (new_id != null && idInUse(new_id))
                return new_id;

            // pick a new unique ID!
            String id = newSessionId(request.hashCode());

            request.setAttribute(__NEW_SESSION_ID, id);
            return id;
        }
    }

    /* ------------------------------------------------------------ */
    public String newSessionId(long seedTerm) {
        // pick a new unique ID!
        String id = null;
        while (id == null || id.length() == 0 || idInUse(id)) {
            long r0 = _weakRandom
                    ? (hashCode() ^ Runtime.getRuntime().freeMemory() ^ _random.nextInt() ^ ((seedTerm) << 32))
                    : _random.nextLong();
            if (r0 < 0)
                r0 = -r0;

            // random chance to reseed
            if (_reseed > 0 && (r0 % _reseed) == 1L) {
                LOG.debug("Reseeding {}", this);
                if (_random instanceof SecureRandom) {
                    SecureRandom secure = (SecureRandom) _random;
                    secure.setSeed(secure.generateSeed(8));
                } else {
                    _random.setSeed(_random.nextLong() ^ System.currentTimeMillis() ^ seedTerm ^ Runtime.getRuntime().freeMemory());
                }
            }

            long r1 = _weakRandom
                    ? (hashCode() ^ Runtime.getRuntime().freeMemory() ^ _random.nextInt() ^ ((seedTerm) << 32))
                    : _random.nextLong();
            if (r1 < 0)
                r1 = -r1;

            id = Long.toString(r0, 36) + Long.toString(r1, 36);

            //add in the id of the node to ensure unique id across cluster
            //NOTE this is different to the node suffix which denotes which node the request was received on
            if (_workerName != null)
                id = _workerName + id;

        }
        return id;
    }


    /* ------------------------------------------------------------ */
    @Override
    public abstract void renewSessionId(String oldClusterId, String oldNodeId, HttpServletRequest request);


    /* ------------------------------------------------------------ */
    @Override
    protected void doStart() throws Exception {
        initRandom();
        _workerAttr = (_workerName != null && _workerName.startsWith("$")) ? _workerName.substring(1) : null;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStop() throws Exception {
    }

    /* ------------------------------------------------------------ */

    /**
     * Set up a random number generator for the sessionids.
     * <p>
     * By preference, use a SecureRandom but allow to be injected.
     */
    public void initRandom() {
        if (_random == null) {
            try {
                _random = new SecureRandom();
            } catch (Exception e) {
                LOG.warn("Could not generate SecureRandom for session-id randomness", e);
                _random = new Random();
                _weakRandom = true;
            }
        } else
            _random.setSeed(_random.nextLong() ^ System.currentTimeMillis() ^ hashCode() ^ Runtime.getRuntime().freeMemory());
    }

    /**
     * Get the session ID with any worker ID.
     *
     * @param clusterId
     * @param request
     * @return sessionId plus any worker ID.
     */
    @Override
    public String getNodeId(String clusterId, HttpServletRequest request) {
        if (_workerName != null) {
            if (_workerAttr == null)
                return clusterId + '.' + _workerName;

            String worker = (String) request.getAttribute(_workerAttr);
            if (worker != null)
                return clusterId + '.' + worker;
        }

        return clusterId;
    }

    /**
     * Get the session ID without any worker ID.
     *
     * @param nodeId the node id
     * @return sessionId without any worker ID.
     */
    @Override
    public String getClusterId(String nodeId) {
        int dot = nodeId.lastIndexOf('.');
        return (dot > 0) ? nodeId.substring(0, dot) : nodeId;
    }


}
