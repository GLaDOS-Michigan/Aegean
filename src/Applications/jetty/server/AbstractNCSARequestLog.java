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

package Applications.jetty.server;

import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.PathMap;
import Applications.jetty.server.handler.StatisticsHandler;
import Applications.jetty.util.DateCache;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.component.AbstractLifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.Locale;

/**
 * Base implementation of the {@link RequestLog} outputs logs in the pseudo-standard NCSA common log format.
 * Configuration options allow a choice between the standard Common Log Format (as used in the 3 log format) and the
 * Combined Log Format (single log format). This log format can be output by most web servers, and almost all web log
 * analysis software can understand these formats.
 */
public abstract class AbstractNCSARequestLog extends AbstractLifeCycle implements RequestLog {
    protected static final Logger LOG = Log.getLogger(AbstractNCSARequestLog.class);

    private static ThreadLocal<StringBuilder> _buffers = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(256);
        }
    };


    private String[] _ignorePaths;
    private boolean _extended;
    private transient PathMap<String> _ignorePathMap;
    private boolean _logLatency = false;
    private boolean _logCookies = false;
    private boolean _logServer = false;
    private boolean _preferProxiedForAddress;
    private transient DateCache _logDateCache;
    private String _logDateFormat = "dd/MMM/yyyy:HH:mm:ss Z";
    private Locale _logLocale = Locale.getDefault();
    private String _logTimeZone = "GMT";

    /* ------------------------------------------------------------ */

    /**
     * Is logging enabled
     */
    protected abstract boolean isEnabled();
    
    /* ------------------------------------------------------------ */

    /**
     * Write requestEntry out. (to disk or slf4j log)
     */
    public abstract void write(String requestEntry) throws IOException;

    /* ------------------------------------------------------------ */

    /**
     * Writes the request and response information to the output stream.
     *
     * @see Applications.jetty.server.RequestLog#log(Applications.jetty.server.Request,
     * Applications.jetty.server.Response)
     */
    @Override
    public void log(Request request, Response response) {
        try {
            if (_ignorePathMap != null && _ignorePathMap.getMatch(request.getRequestURI()) != null)
                return;

            if (!isEnabled())
                return;

            StringBuilder buf = _buffers.get();
            buf.setLength(0);

            if (_logServer) {
                buf.append(request.getServerName());
                buf.append(' ');
            }

            String addr = null;
            if (_preferProxiedForAddress) {
                addr = request.getHeader(HttpHeader.X_FORWARDED_FOR.toString());
            }

            if (addr == null)
                addr = request.getRemoteAddr();

            buf.append(addr);
            buf.append(" - ");
            Authentication authentication = request.getAuthentication();
            if (authentication instanceof Authentication.User)
                buf.append(((Authentication.User) authentication).getUserIdentity().getUserPrincipal().getName());
            else
                buf.append(" - ");

            buf.append(" [");
            if (_logDateCache != null)
                buf.append(_logDateCache.format(request.getTimeStamp()));
            else
                buf.append(request.getTimeStamp());

            buf.append("] \"");
            buf.append(request.getMethod());
            buf.append(' ');
            buf.append(request.getUri().toString());
            buf.append(' ');
            buf.append(request.getProtocol());
            buf.append("\" ");

            int status = response.getStatus();
            if (status <= 0)
                status = 404;
            buf.append((char) ('0' + ((status / 100) % 10)));
            buf.append((char) ('0' + ((status / 10) % 10)));
            buf.append((char) ('0' + (status % 10)));

            long responseLength = response.getLongContentLength();
            if (responseLength >= 0) {
                buf.append(' ');
                if (responseLength > 99999)
                    buf.append(responseLength);
                else {
                    if (responseLength > 9999)
                        buf.append((char) ('0' + ((responseLength / 10000) % 10)));
                    if (responseLength > 999)
                        buf.append((char) ('0' + ((responseLength / 1000) % 10)));
                    if (responseLength > 99)
                        buf.append((char) ('0' + ((responseLength / 100) % 10)));
                    if (responseLength > 9)
                        buf.append((char) ('0' + ((responseLength / 10) % 10)));
                    buf.append((char) ('0' + (responseLength) % 10));
                }
                buf.append(' ');
            } else
                buf.append(" - ");


            if (_extended)
                logExtended(request, response, buf);

            if (_logCookies) {
                Cookie[] cookies = request.getCookies();
                if (cookies == null || cookies.length == 0)
                    buf.append(" -");
                else {
                    buf.append(" \"");
                    for (int i = 0; i < cookies.length; i++) {
                        if (i != 0)
                            buf.append(';');
                        buf.append(cookies[i].getName());
                        buf.append('=');
                        buf.append(cookies[i].getValue());
                    }
                    buf.append('\"');
                }
            }

            if (_logLatency) {
                long now = System.currentTimeMillis();

                if (_logLatency) {
                    buf.append(' ');
                    buf.append(now - request.getTimeStamp());
                }
            }

            String log = buf.toString();
            write(log);
        } catch (IOException e) {
            LOG.warn(e);
        }
    }
    
    /* ------------------------------------------------------------ */

    /**
     * Writes extended request and response information to the output stream.
     *
     * @param request  request object
     * @param response response object
     * @param b        StringBuilder to write to
     * @throws IOException
     */
    protected void logExtended(Request request,
                               Response response,
                               StringBuilder b) throws IOException {
        String referer = request.getHeader(HttpHeader.REFERER.toString());
        if (referer == null)
            b.append("\"-\" ");
        else {
            b.append('"');
            b.append(referer);
            b.append("\" ");
        }

        String agent = request.getHeader(HttpHeader.USER_AGENT.toString());
        if (agent == null)
            b.append("\"-\" ");
        else {
            b.append('"');
            b.append(agent);
            b.append('"');
        }
    }


    /**
     * Set request paths that will not be logged.
     *
     * @param ignorePaths array of request paths
     */
    public void setIgnorePaths(String[] ignorePaths) {
        _ignorePaths = ignorePaths;
    }

    /**
     * Retrieve the request paths that will not be logged.
     *
     * @return array of request paths
     */
    public String[] getIgnorePaths() {
        return _ignorePaths;
    }

    /**
     * Controls logging of the request cookies.
     *
     * @param logCookies true - values of request cookies will be logged, false - values of request cookies will not be
     *                   logged
     */
    public void setLogCookies(boolean logCookies) {
        _logCookies = logCookies;
    }

    /**
     * Retrieve log cookies flag
     *
     * @return value of the flag
     */
    public boolean getLogCookies() {
        return _logCookies;
    }

    /**
     * Controls logging of the request hostname.
     *
     * @param logServer true - request hostname will be logged, false - request hostname will not be logged
     */
    public void setLogServer(boolean logServer) {
        _logServer = logServer;
    }

    /**
     * Retrieve log hostname flag.
     *
     * @return value of the flag
     */
    public boolean getLogServer() {
        return _logServer;
    }

    /**
     * Controls logging of request processing time.
     *
     * @param logLatency true - request processing time will be logged false - request processing time will not be
     *                   logged
     */
    public void setLogLatency(boolean logLatency) {
        _logLatency = logLatency;
    }

    /**
     * Retrieve log request processing time flag.
     *
     * @return value of the flag
     */
    public boolean getLogLatency() {
        return _logLatency;
    }

    /**
     * @deprecated use {@link StatisticsHandler}
     */
    public void setLogDispatch(boolean value) {
    }

    /**
     * @deprecated use {@link StatisticsHandler}
     */
    public boolean isLogDispatch() {
        return false;
    }

    /**
     * Controls whether the actual IP address of the connection or the IP address from the X-Forwarded-For header will
     * be logged.
     *
     * @param preferProxiedForAddress true - IP address from header will be logged, false - IP address from the
     *                                connection will be logged
     */
    public void setPreferProxiedForAddress(boolean preferProxiedForAddress) {
        _preferProxiedForAddress = preferProxiedForAddress;
    }

    /**
     * Retrieved log X-Forwarded-For IP address flag.
     *
     * @return value of the flag
     */
    public boolean getPreferProxiedForAddress() {
        return _preferProxiedForAddress;
    }

    /**
     * Set the extended request log format flag.
     *
     * @param extended true - log the extended request information, false - do not log the extended request information
     */
    public void setExtended(boolean extended) {
        _extended = extended;
    }

    /**
     * Retrieve the extended request log format flag.
     *
     * @return value of the flag
     */
    @ManagedAttribute("use extended NCSA format")
    public boolean isExtended() {
        return _extended;
    }

    /**
     * Set up request logging and open log file.
     *
     * @see Applications.jetty.util.component.AbstractLifeCycle#doStart()
     */
    @Override
    protected synchronized void doStart() throws Exception {
        if (_logDateFormat != null) {
            _logDateCache = new DateCache(_logDateFormat, _logLocale, _logTimeZone);
        }

        if (_ignorePaths != null && _ignorePaths.length > 0) {
            _ignorePathMap = new PathMap<>();
            for (int i = 0; i < _ignorePaths.length; i++)
                _ignorePathMap.put(_ignorePaths[i], _ignorePaths[i]);
        } else
            _ignorePathMap = null;

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        _logDateCache = null;
        super.doStop();
    }

    /**
     * Set the timestamp format for request log entries in the file. If this is not set, the pre-formated request
     * timestamp is used.
     *
     * @param format timestamp format string
     */
    public void setLogDateFormat(String format) {
        _logDateFormat = format;
    }

    /**
     * Retrieve the timestamp format string for request log entries.
     *
     * @return timestamp format string.
     */
    public String getLogDateFormat() {
        return _logDateFormat;
    }

    /**
     * Set the locale of the request log.
     *
     * @param logLocale locale object
     */
    public void setLogLocale(Locale logLocale) {
        _logLocale = logLocale;
    }

    /**
     * Retrieve the locale of the request log.
     *
     * @return locale object
     */
    public Locale getLogLocale() {
        return _logLocale;
    }

    /**
     * Set the timezone of the request log.
     *
     * @param tz timezone string
     */
    public void setLogTimeZone(String tz) {
        _logTimeZone = tz;
    }

    /**
     * Retrieve the timezone of the request log.
     *
     * @return timezone string
     */
    @ManagedAttribute("the timezone")
    public String getLogTimeZone() {
        return _logTimeZone;
    }
}
