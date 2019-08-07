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

package Applications.jetty.servlets;

import Applications.jetty.continuation.Continuation;
import Applications.jetty.continuation.ContinuationListener;
import Applications.jetty.continuation.ContinuationSupport;
import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.annotation.Name;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.thread.ScheduledExecutorScheduler;
import Applications.jetty.util.thread.Scheduler;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Denial of Service filter
 * <p/>
 * <p>
 * This filter is useful for limiting
 * exposure to abuse from request flooding, whether malicious, or as a result of
 * a misconfigured client.
 * <p>
 * The filter keeps track of the number of requests from a connection per
 * second. If a limit is exceeded, the request is either rejected, delayed, or
 * throttled.
 * <p>
 * When a request is throttled, it is placed in a priority queue. Priority is
 * given first to authenticated users and users with an HttpSession, then
 * connections which can be identified by their IP addresses. Connections with
 * no way to identify them are given lowest priority.
 * <p>
 * The {@link #extractUserId(ServletRequest request)} function should be
 * implemented, in order to uniquely identify authenticated users.
 * <p>
 * The following init parameters control the behavior of the filter:<dl>
 * <p/>
 * <dt>maxRequestsPerSec</dt>
 * <dd>the maximum number of requests from a connection per
 * second. Requests in excess of this are first delayed,
 * then throttled.</dd>
 * <p/>
 * <dt>delayMs</dt>
 * <dd>is the delay given to all requests over the rate limit,
 * before they are considered at all. -1 means just reject request,
 * 0 means no delay, otherwise it is the delay.</dd>
 * <p/>
 * <dt>maxWaitMs</dt>
 * <dd>how long to blocking wait for the throttle semaphore.</dd>
 * <p/>
 * <dt>throttledRequests</dt>
 * <dd>is the number of requests over the rate limit able to be
 * considered at once.</dd>
 * <p/>
 * <dt>throttleMs</dt>
 * <dd>how long to async wait for semaphore.</dd>
 * <p/>
 * <dt>maxRequestMs</dt>
 * <dd>how long to allow this request to run.</dd>
 * <p/>
 * <dt>maxIdleTrackerMs</dt>
 * <dd>how long to keep track of request rates for a connection,
 * before deciding that the user has gone away, and discarding it</dd>
 * <p/>
 * <dt>insertHeaders</dt>
 * <dd>if true , insert the DoSFilter headers into the response. Defaults to true.</dd>
 * <p/>
 * <dt>trackSessions</dt>
 * <dd>if true, usage rate is tracked by session if a session exists. Defaults to true.</dd>
 * <p/>
 * <dt>remotePort</dt>
 * <dd>if true and session tracking is not used, then rate is tracked by IP+port (effectively connection). Defaults to false.</dd>
 * <p/>
 * <dt>ipWhitelist</dt>
 * <dd>a comma-separated list of IP addresses that will not be rate limited</dd>
 * <p/>
 * <dt>managedAttr</dt>
 * <dd>if set to true, then this servlet is set as a {@link ServletContext} attribute with the
 * filter name as the attribute name.  This allows context external mechanism (eg JMX via {@link ContextHandler#MANAGED_ATTRIBUTES}) to
 * manage the configuration of the filter.</dd>
 * </dl>
 * </p>
 */
@ManagedObject("limits exposure to abuse from request flooding, whether malicious, or as a result of a misconfigured client")
public class DoSFilter implements Filter {
    private static final Logger LOG = Log.getLogger(DoSFilter.class);

    private static final String IPv4_GROUP = "(\\d{1,3})";
    private static final Pattern IPv4_PATTERN = Pattern.compile(IPv4_GROUP + "\\." + IPv4_GROUP + "\\." + IPv4_GROUP + "\\." + IPv4_GROUP);
    private static final String IPv6_GROUP = "(\\p{XDigit}{1,4})";
    private static final Pattern IPv6_PATTERN = Pattern.compile(IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP + ":" + IPv6_GROUP);
    private static final Pattern CIDR_PATTERN = Pattern.compile("([^/]+)/(\\d+)");

    private static final String __TRACKER = "DoSFilter.Tracker";
    private static final String __THROTTLED = "DoSFilter.Throttled";

    private static final int __DEFAULT_MAX_REQUESTS_PER_SEC = 25;
    private static final int __DEFAULT_DELAY_MS = 100;
    private static final int __DEFAULT_THROTTLE = 5;
    private static final int __DEFAULT_MAX_WAIT_MS = 50;
    private static final long __DEFAULT_THROTTLE_MS = 30000L;
    private static final long __DEFAULT_MAX_REQUEST_MS_INIT_PARAM = 30000L;
    private static final long __DEFAULT_MAX_IDLE_TRACKER_MS_INIT_PARAM = 30000L;

    static final String MANAGED_ATTR_INIT_PARAM = "managedAttr";
    static final String MAX_REQUESTS_PER_S_INIT_PARAM = "maxRequestsPerSec";
    static final String DELAY_MS_INIT_PARAM = "delayMs";
    static final String THROTTLED_REQUESTS_INIT_PARAM = "throttledRequests";
    static final String MAX_WAIT_INIT_PARAM = "maxWaitMs";
    static final String THROTTLE_MS_INIT_PARAM = "throttleMs";
    static final String MAX_REQUEST_MS_INIT_PARAM = "maxRequestMs";
    static final String MAX_IDLE_TRACKER_MS_INIT_PARAM = "maxIdleTrackerMs";
    static final String INSERT_HEADERS_INIT_PARAM = "insertHeaders";
    static final String TRACK_SESSIONS_INIT_PARAM = "trackSessions";
    static final String REMOTE_PORT_INIT_PARAM = "remotePort";
    static final String IP_WHITELIST_INIT_PARAM = "ipWhitelist";
    static final String ENABLED_INIT_PARAM = "enabled";

    private static final int USER_AUTH = 2;
    private static final int USER_SESSION = 2;
    private static final int USER_IP = 1;
    private static final int USER_UNKNOWN = 0;

    private ServletContext _context;
    private volatile long _delayMs;
    private volatile long _throttleMs;
    private volatile long _maxWaitMs;
    private volatile long _maxRequestMs;
    private volatile long _maxIdleTrackerMs;
    private volatile boolean _insertHeaders;
    private volatile boolean _trackSessions;
    private volatile boolean _remotePort;
    private volatile boolean _enabled;
    private Semaphore _passes;
    private volatile int _throttledRequests;
    private volatile int _maxRequestsPerSec;
    private Queue<Continuation>[] _queue;
    private ContinuationListener[] _listeners;
    private final ConcurrentHashMap<String, RateTracker> _rateTrackers = new ConcurrentHashMap<>();
    private final List<String> _whitelist = new CopyOnWriteArrayList<>();
    private Scheduler _scheduler;

    public void init(FilterConfig filterConfig) throws ServletException {
        _context = filterConfig.getServletContext();

        _queue = new Queue[getMaxPriority() + 1];
        _listeners = new ContinuationListener[getMaxPriority() + 1];
        for (int p = 0; p < _queue.length; p++) {
            _queue[p] = new ConcurrentLinkedQueue<>();

            final int priority = p;
            _listeners[p] = new ContinuationListener() {
                public void onComplete(Continuation continuation) {
                }

                public void onTimeout(Continuation continuation) {
                    _queue[priority].remove(continuation);
                }
            };
        }

        _rateTrackers.clear();

        int maxRequests = __DEFAULT_MAX_REQUESTS_PER_SEC;
        String parameter = filterConfig.getInitParameter(MAX_REQUESTS_PER_S_INIT_PARAM);
        if (parameter != null)
            maxRequests = Integer.parseInt(parameter);
        setMaxRequestsPerSec(maxRequests);

        long delay = __DEFAULT_DELAY_MS;
        parameter = filterConfig.getInitParameter(DELAY_MS_INIT_PARAM);
        if (parameter != null)
            delay = Long.parseLong(parameter);
        setDelayMs(delay);

        int throttledRequests = __DEFAULT_THROTTLE;
        parameter = filterConfig.getInitParameter(THROTTLED_REQUESTS_INIT_PARAM);
        if (parameter != null)
            throttledRequests = Integer.parseInt(parameter);
        setThrottledRequests(throttledRequests);

        long maxWait = __DEFAULT_MAX_WAIT_MS;
        parameter = filterConfig.getInitParameter(MAX_WAIT_INIT_PARAM);
        if (parameter != null)
            maxWait = Long.parseLong(parameter);
        setMaxWaitMs(maxWait);

        long throttle = __DEFAULT_THROTTLE_MS;
        parameter = filterConfig.getInitParameter(THROTTLE_MS_INIT_PARAM);
        if (parameter != null)
            throttle = Long.parseLong(parameter);
        setThrottleMs(throttle);

        long maxRequestMs = __DEFAULT_MAX_REQUEST_MS_INIT_PARAM;
        parameter = filterConfig.getInitParameter(MAX_REQUEST_MS_INIT_PARAM);
        if (parameter != null)
            maxRequestMs = Long.parseLong(parameter);
        setMaxRequestMs(maxRequestMs);

        long maxIdleTrackerMs = __DEFAULT_MAX_IDLE_TRACKER_MS_INIT_PARAM;
        parameter = filterConfig.getInitParameter(MAX_IDLE_TRACKER_MS_INIT_PARAM);
        if (parameter != null)
            maxIdleTrackerMs = Long.parseLong(parameter);
        setMaxIdleTrackerMs(maxIdleTrackerMs);

        String whiteList = "";
        parameter = filterConfig.getInitParameter(IP_WHITELIST_INIT_PARAM);
        if (parameter != null)
            whiteList = parameter;
        setWhitelist(whiteList);

        parameter = filterConfig.getInitParameter(INSERT_HEADERS_INIT_PARAM);
        setInsertHeaders(parameter == null || Boolean.parseBoolean(parameter));

        parameter = filterConfig.getInitParameter(TRACK_SESSIONS_INIT_PARAM);
        setTrackSessions(parameter == null || Boolean.parseBoolean(parameter));

        parameter = filterConfig.getInitParameter(REMOTE_PORT_INIT_PARAM);
        setRemotePort(parameter != null && Boolean.parseBoolean(parameter));

        parameter = filterConfig.getInitParameter(ENABLED_INIT_PARAM);
        setEnabled(parameter == null || Boolean.parseBoolean(parameter));

        _scheduler = startScheduler();

        if (_context != null && Boolean.parseBoolean(filterConfig.getInitParameter(MANAGED_ATTR_INIT_PARAM)))
            _context.setAttribute(filterConfig.getFilterName(), this);
    }

    protected Scheduler startScheduler() throws ServletException {
        try {
            Scheduler result = new ScheduledExecutorScheduler();
            result.start();
            return result;
        } catch (Exception x) {
            throw new ServletException(x);
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        doFilter((HttpServletRequest) request, (HttpServletResponse) response, filterChain);
    }

    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (!isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Look for the rate tracker for this request
        RateTracker tracker = (RateTracker) request.getAttribute(__TRACKER);

        if (tracker == null) {
            // This is the first time we have seen this request.

            // get a rate tracker associated with this request, and record one hit
            tracker = getRateTracker(request);

            // Calculate the rate and check it is over the allowed limit
            final boolean overRateLimit = tracker.isRateExceeded(System.currentTimeMillis());

            // pass it through if  we are not currently over the rate limit
            if (!overRateLimit) {
                doFilterChain(filterChain, request, response);
                return;
            }

            // We are over the limit.

            // So either reject it, delay it or throttle it
            long delayMs = getDelayMs();
            boolean insertHeaders = isInsertHeaders();
            switch ((int) delayMs) {
                case -1: {
                    // Reject this request
                    LOG.warn("DOS ALERT: Request rejected ip=" + request.getRemoteAddr() + ",session=" + request.getRequestedSessionId() + ",user=" + request.getUserPrincipal());
                    if (insertHeaders)
                        response.addHeader("DoSFilter", "unavailable");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
                case 0: {
                    // fall through to throttle code
                    LOG.warn("DOS ALERT: Request throttled ip=" + request.getRemoteAddr() + ",session=" + request.getRequestedSessionId() + ",user=" + request.getUserPrincipal());
                    request.setAttribute(__TRACKER, tracker);
                    break;
                }
                default: {
                    // insert a delay before throttling the request
                    LOG.warn("DOS ALERT: Request delayed=" + delayMs + "ms ip=" + request.getRemoteAddr() + ",session=" + request.getRequestedSessionId() + ",user=" + request.getUserPrincipal());
                    if (insertHeaders)
                        response.addHeader("DoSFilter", "delayed");
                    Continuation continuation = ContinuationSupport.getContinuation(request);
                    request.setAttribute(__TRACKER, tracker);
                    if (delayMs > 0)
                        continuation.setTimeout(delayMs);
                    continuation.suspend();
                    return;
                }
            }
        }

        // Throttle the request
        boolean accepted = false;
        try {
            // check if we can afford to accept another request at this time
            accepted = _passes.tryAcquire(getMaxWaitMs(), TimeUnit.MILLISECONDS);

            if (!accepted) {
                // we were not accepted, so either we suspend to wait,or if we were woken up we insist or we fail
                final Continuation continuation = ContinuationSupport.getContinuation(request);

                Boolean throttled = (Boolean) request.getAttribute(__THROTTLED);
                long throttleMs = getThrottleMs();
                if (throttled != Boolean.TRUE && throttleMs > 0) {
                    int priority = getPriority(request, tracker);
                    request.setAttribute(__THROTTLED, Boolean.TRUE);
                    if (isInsertHeaders())
                        response.addHeader("DoSFilter", "throttled");
                    if (throttleMs > 0)
                        continuation.setTimeout(throttleMs);
                    continuation.suspend();

                    continuation.addContinuationListener(_listeners[priority]);
                    _queue[priority].add(continuation);
                    return;
                }
                // else were we resumed?
                else if (request.getAttribute("javax.servlet.resumed") == Boolean.TRUE) {
                    // we were resumed and somebody stole our pass, so we wait for the next one.
                    _passes.acquire();
                    accepted = true;
                }
            }

            // if we were accepted (either immediately or after throttle)
            if (accepted)
                // call the chain
                doFilterChain(filterChain, request, response);
            else {
                // fail the request
                if (isInsertHeaders())
                    response.addHeader("DoSFilter", "unavailable");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        } catch (InterruptedException e) {
            _context.log("DoS", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } finally {
            if (accepted) {
                // wake up the next highest priority request.
                for (int p = _queue.length; p-- > 0; ) {
                    Continuation continuation = _queue[p].poll();
                    if (continuation != null && continuation.isSuspended()) {
                        continuation.resume();
                        break;
                    }
                }
                _passes.release();
            }
        }
    }

    protected void doFilterChain(FilterChain chain, final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        final Thread thread = Thread.currentThread();
        Runnable requestTimeout = new Runnable() {
            @Override
            public void run() {
                closeConnection(request, response, thread);
            }
        };

        Scheduler.Task task = _scheduler.schedule(requestTimeout, getMaxRequestMs(), TimeUnit.MILLISECONDS);
        try {
            chain.doFilter(request, response);
        } finally {
            task.cancel();
        }
    }

    /**
     * Takes drastic measures to return this response and stop this thread.
     * Due to the way the connection is interrupted, may return mixed up headers.
     *
     * @param request  current request
     * @param response current response, which must be stopped
     * @param thread   the handling thread
     */
    protected void closeConnection(HttpServletRequest request, HttpServletResponse response, Thread thread) {
        // take drastic measures to return this response and stop this thread.
        if (!response.isCommitted()) {
            response.setHeader("Connection", "close");
        }
        try {
            try {
                response.getWriter().close();
            } catch (IllegalStateException e) {
                response.getOutputStream().close();
            }
        } catch (IOException e) {
            LOG.warn(e);
        }

        // interrupt the handling thread
        thread.interrupt();
    }

    /**
     * Get priority for this request, based on user type
     *
     * @param request the current request
     * @param tracker the rate tracker for this request
     * @return the priority for this request
     */
    protected int getPriority(HttpServletRequest request, RateTracker tracker) {
        if (extractUserId(request) != null)
            return USER_AUTH;
        if (tracker != null)
            return tracker.getType();
        return USER_UNKNOWN;
    }

    /**
     * @return the maximum priority that we can assign to a request
     */
    protected int getMaxPriority() {
        return USER_AUTH;
    }

    /**
     * Return a request rate tracker associated with this connection; keeps
     * track of this connection's request rate. If this is not the first request
     * from this connection, return the existing object with the stored stats.
     * If it is the first request, then create a new request tracker.
     * <p/>
     * Assumes that each connection has an identifying characteristic, and goes
     * through them in order, taking the first that matches: user id (logged
     * in), session id, client IP address. Unidentifiable connections are lumped
     * into one.
     * <p/>
     * When a session expires, its rate tracker is automatically deleted.
     *
     * @param request the current request
     * @return the request rate tracker for the current connection
     */
    public RateTracker getRateTracker(ServletRequest request) {
        HttpSession session = ((HttpServletRequest) request).getSession(false);

        String loadId = extractUserId(request);
        final int type;
        if (loadId != null) {
            type = USER_AUTH;
        } else {
            if (isTrackSessions() && session != null && !session.isNew()) {
                loadId = session.getId();
                type = USER_SESSION;
            } else {
                loadId = isRemotePort() ? (request.getRemoteAddr() + request.getRemotePort()) : request.getRemoteAddr();
                type = USER_IP;
            }
        }

        RateTracker tracker = _rateTrackers.get(loadId);

        if (tracker == null) {
            boolean allowed = checkWhitelist(_whitelist, request.getRemoteAddr());
            int maxRequestsPerSec = getMaxRequestsPerSec();
            tracker = allowed ? new FixedRateTracker(loadId, type, maxRequestsPerSec)
                    : new RateTracker(loadId, type, maxRequestsPerSec);
            RateTracker existing = _rateTrackers.putIfAbsent(loadId, tracker);
            if (existing != null)
                tracker = existing;

            if (type == USER_IP) {
                // USER_IP expiration from _rateTrackers is handled by the _scheduler
                _scheduler.schedule(tracker, getMaxIdleTrackerMs(), TimeUnit.MILLISECONDS);
            } else if (session != null) {
                // USER_SESSION expiration from _rateTrackers are handled by the HttpSessionBindingListener
                session.setAttribute(__TRACKER, tracker);
            }
        }

        return tracker;
    }

    protected boolean checkWhitelist(List<String> whitelist, String candidate) {
        for (String address : whitelist) {
            if (address.contains("/")) {
                if (subnetMatch(address, candidate))
                    return true;
            } else {
                if (address.equals(candidate))
                    return true;
            }
        }
        return false;
    }

    protected boolean subnetMatch(String subnetAddress, String address) {
        Matcher cidrMatcher = CIDR_PATTERN.matcher(subnetAddress);
        if (!cidrMatcher.matches())
            return false;

        String subnet = cidrMatcher.group(1);
        int prefix;
        try {
            prefix = Integer.parseInt(cidrMatcher.group(2));
        } catch (NumberFormatException x) {
            LOG.info("Ignoring malformed CIDR address {}", subnetAddress);
            return false;
        }

        byte[] subnetBytes = addressToBytes(subnet);
        if (subnetBytes == null) {
            LOG.info("Ignoring malformed CIDR address {}", subnetAddress);
            return false;
        }
        byte[] addressBytes = addressToBytes(address);
        if (addressBytes == null) {
            LOG.info("Ignoring malformed remote address {}", address);
            return false;
        }

        // Comparing IPv4 with IPv6 ?
        int length = subnetBytes.length;
        if (length != addressBytes.length)
            return false;

        byte[] mask = prefixToBytes(prefix, length);

        for (int i = 0; i < length; ++i) {
            if ((subnetBytes[i] & mask[i]) != (addressBytes[i] & mask[i]))
                return false;
        }

        return true;
    }

    private byte[] addressToBytes(String address) {
        Matcher ipv4Matcher = IPv4_PATTERN.matcher(address);
        if (ipv4Matcher.matches()) {
            byte[] result = new byte[4];
            for (int i = 0; i < result.length; ++i)
                result[i] = Integer.valueOf(ipv4Matcher.group(i + 1)).byteValue();
            return result;
        } else {
            Matcher ipv6Matcher = IPv6_PATTERN.matcher(address);
            if (ipv6Matcher.matches()) {
                byte[] result = new byte[16];
                for (int i = 0; i < result.length; i += 2) {
                    int word = Integer.valueOf(ipv6Matcher.group(i / 2 + 1), 16);
                    result[i] = (byte) ((word & 0xFF00) >>> 8);
                    result[i + 1] = (byte) (word & 0xFF);
                }
                return result;
            }
        }
        return null;
    }

    private byte[] prefixToBytes(int prefix, int length) {
        byte[] result = new byte[length];
        int index = 0;
        while (prefix / 8 > 0) {
            result[index] = -1;
            prefix -= 8;
            ++index;
        }
        // Sets the _prefix_ most significant bits to 1
        result[index] = (byte) ~((1 << (8 - prefix)) - 1);
        return result;
    }

    public void destroy() {
        LOG.debug("Destroy {}", this);
        stopScheduler();
        _rateTrackers.clear();
        _whitelist.clear();
    }

    protected void stopScheduler() {
        try {
            _scheduler.stop();
        } catch (Exception x) {
            LOG.ignore(x);
        }
    }

    /**
     * Returns the user id, used to track this connection.
     * This SHOULD be overridden by subclasses.
     *
     * @param request the current request
     * @return a unique user id, if logged in; otherwise null.
     */
    protected String extractUserId(ServletRequest request) {
        return null;
    }

    /**
     * Get maximum number of requests from a connection per
     * second. Requests in excess of this are first delayed,
     * then throttled.
     *
     * @return maximum number of requests
     */
    @ManagedAttribute("maximum number of requests allowed from a connection per second")
    public int getMaxRequestsPerSec() {
        return _maxRequestsPerSec;
    }

    /**
     * Get maximum number of requests from a connection per
     * second. Requests in excess of this are first delayed,
     * then throttled.
     *
     * @param value maximum number of requests
     */
    public void setMaxRequestsPerSec(int value) {
        _maxRequestsPerSec = value;
    }

    /**
     * Get delay (in milliseconds) that is applied to all requests
     * over the rate limit, before they are considered at all.
     */
    @ManagedAttribute("delay applied to all requests over the rate limit (in ms)")
    public long getDelayMs() {
        return _delayMs;
    }

    /**
     * Set delay (in milliseconds) that is applied to all requests
     * over the rate limit, before they are considered at all.
     *
     * @param value delay (in milliseconds), 0 - no delay, -1 - reject request
     */
    public void setDelayMs(long value) {
        _delayMs = value;
    }

    /**
     * Get maximum amount of time (in milliseconds) the filter will
     * blocking wait for the throttle semaphore.
     *
     * @return maximum wait time
     */
    @ManagedAttribute("maximum time the filter will block waiting throttled connections, (0 for no delay, -1 to reject requests)")
    public long getMaxWaitMs() {
        return _maxWaitMs;
    }

    /**
     * Set maximum amount of time (in milliseconds) the filter will
     * blocking wait for the throttle semaphore.
     *
     * @param value maximum wait time
     */
    public void setMaxWaitMs(long value) {
        _maxWaitMs = value;
    }

    /**
     * Get number of requests over the rate limit able to be
     * considered at once.
     *
     * @return number of requests
     */
    @ManagedAttribute("number of requests over rate limit")
    public int getThrottledRequests() {
        return _throttledRequests;
    }

    /**
     * Set number of requests over the rate limit able to be
     * considered at once.
     *
     * @param value number of requests
     */
    public void setThrottledRequests(int value) {
        int permits = _passes == null ? 0 : _passes.availablePermits();
        _passes = new Semaphore((value - _throttledRequests + permits), true);
        _throttledRequests = value;
    }

    /**
     * Get amount of time (in milliseconds) to async wait for semaphore.
     *
     * @return wait time
     */
    @ManagedAttribute("amount of time to async wait for semaphore")
    public long getThrottleMs() {
        return _throttleMs;
    }

    /**
     * Set amount of time (in milliseconds) to async wait for semaphore.
     *
     * @param value wait time
     */
    public void setThrottleMs(long value) {
        _throttleMs = value;
    }

    /**
     * Get maximum amount of time (in milliseconds) to allow
     * the request to process.
     *
     * @return maximum processing time
     */
    @ManagedAttribute("maximum time to allow requests to process (in ms)")
    public long getMaxRequestMs() {
        return _maxRequestMs;
    }

    /**
     * Set maximum amount of time (in milliseconds) to allow
     * the request to process.
     *
     * @param value maximum processing time
     */
    public void setMaxRequestMs(long value) {
        _maxRequestMs = value;
    }

    /**
     * Get maximum amount of time (in milliseconds) to keep track
     * of request rates for a connection, before deciding that
     * the user has gone away, and discarding it.
     *
     * @return maximum tracking time
     */
    @ManagedAttribute("maximum time to track of request rates for connection before discarding")
    public long getMaxIdleTrackerMs() {
        return _maxIdleTrackerMs;
    }

    /**
     * Set maximum amount of time (in milliseconds) to keep track
     * of request rates for a connection, before deciding that
     * the user has gone away, and discarding it.
     *
     * @param value maximum tracking time
     */
    public void setMaxIdleTrackerMs(long value) {
        _maxIdleTrackerMs = value;
    }

    /**
     * Check flag to insert the DoSFilter headers into the response.
     *
     * @return value of the flag
     */
    @ManagedAttribute("inser DoSFilter headers in response")
    public boolean isInsertHeaders() {
        return _insertHeaders;
    }

    /**
     * Set flag to insert the DoSFilter headers into the response.
     *
     * @param value value of the flag
     */
    public void setInsertHeaders(boolean value) {
        _insertHeaders = value;
    }

    /**
     * Get flag to have usage rate tracked by session if a session exists.
     *
     * @return value of the flag
     */
    @ManagedAttribute("usage rate is tracked by session if one exists")
    public boolean isTrackSessions() {
        return _trackSessions;
    }

    /**
     * Set flag to have usage rate tracked by session if a session exists.
     *
     * @param value value of the flag
     */
    public void setTrackSessions(boolean value) {
        _trackSessions = value;
    }

    /**
     * Get flag to have usage rate tracked by IP+port (effectively connection)
     * if session tracking is not used.
     *
     * @return value of the flag
     */
    @ManagedAttribute("usage rate is tracked by IP+port is session tracking not used")
    public boolean isRemotePort() {
        return _remotePort;
    }

    /**
     * Set flag to have usage rate tracked by IP+port (effectively connection)
     * if session tracking is not used.
     *
     * @param value value of the flag
     */
    public void setRemotePort(boolean value) {
        _remotePort = value;
    }

    /**
     * @return whether this filter is enabled
     */
    @ManagedAttribute("whether this filter is enabled")
    public boolean isEnabled() {
        return _enabled;
    }

    /**
     * @param enabled whether this filter is enabled
     */
    public void setEnabled(boolean enabled) {
        _enabled = enabled;
    }

    /**
     * Get a list of IP addresses that will not be rate limited.
     *
     * @return comma-separated whitelist
     */
    @ManagedAttribute("list of IPs that will not be rate limited")
    public String getWhitelist() {
        StringBuilder result = new StringBuilder();
        for (Iterator<String> iterator = _whitelist.iterator(); iterator.hasNext(); ) {
            String address = iterator.next();
            result.append(address);
            if (iterator.hasNext())
                result.append(",");
        }
        return result.toString();
    }

    /**
     * Set a list of IP addresses that will not be rate limited.
     *
     * @param value comma-separated whitelist
     */
    public void setWhitelist(String value) {
        List<String> result = new ArrayList<>();
        for (String address : value.split(","))
            addWhitelistAddress(result, address);
        clearWhitelist();
        _whitelist.addAll(result);
        LOG.debug("Whitelisted IP addresses: {}", result);
    }

    /**
     * Clears the list of whitelisted IP addresses
     */
    @ManagedOperation("clears the list of IP addresses that will not be rate limited")
    public void clearWhitelist() {
        _whitelist.clear();
    }

    /**
     * Adds the given IP address, either in the form of a dotted decimal notation A.B.C.D
     * or in the CIDR notation A.B.C.D/M, to the list of whitelisted IP addresses.
     *
     * @param address the address to add
     * @return whether the address was added to the list
     * @see #removeWhitelistAddress(String)
     */
    @ManagedOperation("adds an IP address that will not be rate limited")
    public boolean addWhitelistAddress(@Name("address") String address) {
        return addWhitelistAddress(_whitelist, address);
    }

    private boolean addWhitelistAddress(List<String> list, String address) {
        address = address.trim();
        return address.length() > 0 && list.add(address);
    }

    /**
     * Removes the given address from the list of whitelisted IP addresses.
     *
     * @param address the address to remove
     * @return whether the address was removed from the list
     * @see #addWhitelistAddress(String)
     */
    @ManagedOperation("removes an IP address that will not be rate limited")
    public boolean removeWhitelistAddress(@Name("address") String address) {
        return _whitelist.remove(address);
    }

    /**
     * A RateTracker is associated with a connection, and stores request rate
     * data.
     */
    class RateTracker implements Runnable, HttpSessionBindingListener, HttpSessionActivationListener, Serializable {
        private static final long serialVersionUID = 3534663738034577872L;

        transient protected final String _id;
        transient protected final int _type;
        transient protected final long[] _timestamps;
        transient protected int _next;

        public RateTracker(String id, int type, int maxRequestsPerSecond) {
            _id = id;
            _type = type;
            _timestamps = new long[maxRequestsPerSecond];
            _next = 0;
        }

        /**
         * @return the current calculated request rate over the last second
         */
        public boolean isRateExceeded(long now) {
            final long last;
            synchronized (this) {
                last = _timestamps[_next];
                _timestamps[_next] = now;
                _next = (_next + 1) % _timestamps.length;
            }

            return last != 0 && (now - last) < 1000L;
        }

        public String getId() {
            return _id;
        }

        public int getType() {
            return _type;
        }

        public void valueBound(HttpSessionBindingEvent event) {
            if (LOG.isDebugEnabled())
                LOG.debug("Value bound: {}", getId());
        }

        public void valueUnbound(HttpSessionBindingEvent event) {
            //take the tracker out of the list of trackers
            _rateTrackers.remove(_id);
            if (LOG.isDebugEnabled())
                LOG.debug("Tracker removed: {}", getId());
        }

        public void sessionWillPassivate(HttpSessionEvent se) {
            //take the tracker of the list of trackers (if its still there)
            //and ensure that we take ourselves out of the session so we are not saved
            _rateTrackers.remove(_id);
            se.getSession().removeAttribute(__TRACKER);
            if (LOG.isDebugEnabled()) LOG.debug("Value removed: {}", getId());
        }

        public void sessionDidActivate(HttpSessionEvent se) {
            LOG.warn("Unexpected session activation");
        }

        @Override
        public void run() {
            int latestIndex = _next == 0 ? (_timestamps.length - 1) : (_next - 1);
            long last = _timestamps[latestIndex];
            boolean hasRecentRequest = last != 0 && (System.currentTimeMillis() - last) < 1000L;

            if (hasRecentRequest)
                _scheduler.schedule(this, getMaxIdleTrackerMs(), TimeUnit.MILLISECONDS);
            else
                _rateTrackers.remove(_id);
        }

        @Override
        public String toString() {
            return "RateTracker/" + _id + "/" + _type;
        }
    }

    class FixedRateTracker extends RateTracker {
        public FixedRateTracker(String id, int type, int numRecentRequestsTracked) {
            super(id, type, numRecentRequestsTracked);
        }

        @Override
        public boolean isRateExceeded(long now) {
            // rate limit is never exceeded, but we keep track of the request timestamps
            // so that we know whether there was recent activity on this tracker
            // and whether it should be expired
            synchronized (this) {
                _timestamps[_next] = now;
                _next = (_next + 1) % _timestamps.length;
            }

            return false;
        }

        @Override
        public String toString() {
            return "Fixed" + super.toString();
        }
    }
}
