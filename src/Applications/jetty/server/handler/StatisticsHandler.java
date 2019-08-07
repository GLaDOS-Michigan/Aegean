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

package Applications.jetty.server.handler;

import Applications.jetty.server.AsyncContextEvent;
import Applications.jetty.server.HttpChannelState;
import Applications.jetty.server.Request;
import Applications.jetty.server.Response;
import Applications.jetty.util.FutureCallback;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;
import Applications.jetty.util.component.Graceful;
import Applications.jetty.util.statistic.CounterStatistic;
import Applications.jetty.util.statistic.SampleStatistic;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@ManagedObject("Request Statistics Gathering")
public class StatisticsHandler extends HandlerWrapper implements Graceful {
    private final AtomicLong _statsStartedAt = new AtomicLong();

    private final CounterStatistic _requestStats = new CounterStatistic();
    private final SampleStatistic _requestTimeStats = new SampleStatistic();
    private final CounterStatistic _dispatchedStats = new CounterStatistic();
    private final SampleStatistic _dispatchedTimeStats = new SampleStatistic();
    private final CounterStatistic _asyncWaitStats = new CounterStatistic();

    private final AtomicInteger _asyncDispatches = new AtomicInteger();
    private final AtomicInteger _expires = new AtomicInteger();

    private final AtomicInteger _responses1xx = new AtomicInteger();
    private final AtomicInteger _responses2xx = new AtomicInteger();
    private final AtomicInteger _responses3xx = new AtomicInteger();
    private final AtomicInteger _responses4xx = new AtomicInteger();
    private final AtomicInteger _responses5xx = new AtomicInteger();
    private final AtomicLong _responsesTotalBytes = new AtomicLong();

    private final AtomicReference<FutureCallback> _shutdown = new AtomicReference<>();

    private final AsyncListener _onCompletion = new AsyncListener() {
        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            _expires.incrementAndGet();
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            event.getAsyncContext().addListener(this);
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            HttpChannelState state = ((AsyncContextEvent) event).getHttpChannelState();

            Request request = state.getBaseRequest();
            final long elapsed = System.currentTimeMillis() - request.getTimeStamp();

            long d = _requestStats.decrement();
            _requestTimeStats.set(elapsed);

            updateResponse(request);

            _asyncWaitStats.decrement();

            // If we have no more dispatches, should we signal shutdown?
            if (d == 0) {
                FutureCallback shutdown = _shutdown.get();
                if (shutdown != null)
                    shutdown.succeeded();
            }
        }
    };

    /**
     * Resets the current request statistics.
     */
    @ManagedOperation(value = "resets statistics", impact = "ACTION")
    public void statsReset() {
        _statsStartedAt.set(System.currentTimeMillis());

        _requestStats.reset();
        _requestTimeStats.reset();
        _dispatchedStats.reset();
        _dispatchedTimeStats.reset();
        _asyncWaitStats.reset();

        _asyncDispatches.set(0);
        _expires.set(0);
        _responses1xx.set(0);
        _responses2xx.set(0);
        _responses3xx.set(0);
        _responses4xx.set(0);
        _responses5xx.set(0);
        _responsesTotalBytes.set(0L);
    }

    @Override
    public void handle(String path, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException, ServletException {
        _dispatchedStats.increment();

        final long start;
        HttpChannelState state = request.getHttpChannelState();
        if (state.isInitial()) {
            // new request
            _requestStats.increment();
            start = request.getTimeStamp();
        } else {
            // resumed request
            start = System.currentTimeMillis();
            _asyncDispatches.incrementAndGet();
        }

        try {
            super.handle(path, request, httpRequest, httpResponse);
        } finally {
            final long now = System.currentTimeMillis();
            final long dispatched = now - start;

            _dispatchedStats.decrement();
            _dispatchedTimeStats.set(dispatched);

            if (state.isSuspended()) {
                if (state.isInitial()) {
                    state.addListener(_onCompletion);
                    _asyncWaitStats.increment();
                }
            } else if (state.isInitial()) {
                long d = _requestStats.decrement();
                _requestTimeStats.set(dispatched);
                updateResponse(request);

                // If we have no more dispatches, should we signal shutdown?
                FutureCallback shutdown = _shutdown.get();
                if (shutdown != null) {
                    httpResponse.flushBuffer();
                    if (d == 0)
                        shutdown.succeeded();
                }
            }
            // else onCompletion will handle it.
        }
    }

    private void updateResponse(Request request) {
        Response response = request.getResponse();
        switch (response.getStatus() / 100) {
            case 0:
                if (request.isHandled())
                    _responses2xx.incrementAndGet();
                else
                    _responses4xx.incrementAndGet();
                break;
            case 1:
                _responses1xx.incrementAndGet();
                break;
            case 2:
                _responses2xx.incrementAndGet();
                break;
            case 3:
                _responses3xx.incrementAndGet();
                break;
            case 4:
                _responses4xx.incrementAndGet();
                break;
            case 5:
                _responses5xx.incrementAndGet();
                break;
            default:
                break;
        }
        _responsesTotalBytes.addAndGet(response.getContentCount());
    }

    @Override
    protected void doStart() throws Exception {
        _shutdown.set(null);
        super.doStart();
        statsReset();
    }


    @Override
    protected void doStop() throws Exception {
        super.doStop();
        FutureCallback shutdown = _shutdown.get();
        if (shutdown != null && !shutdown.isDone())
            shutdown.failed(new TimeoutException());
    }

    /**
     * @return the number of requests handled by this handler
     * since {@link #statsReset()} was last called, excluding
     * active requests
     * @see #getAsyncDispatches()
     */
    @ManagedAttribute("number of requests")
    public int getRequests() {
        return (int) _requestStats.getTotal();
    }

    /**
     * @return the number of requests currently active.
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests currently active")
    public int getRequestsActive() {
        return (int) _requestStats.getCurrent();
    }

    /**
     * @return the maximum number of active requests
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("maximum number of active requests")
    public int getRequestsActiveMax() {
        return (int) _requestStats.getMax();
    }

    /**
     * @return the maximum time (in milliseconds) of request handling
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("maximum time spend handling requests (in ms)")
    public long getRequestTimeMax() {
        return _requestTimeStats.getMax();
    }

    /**
     * @return the total time (in milliseconds) of requests handling
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("total time spend in all request handling (in ms)")
    public long getRequestTimeTotal() {
        return _requestTimeStats.getTotal();
    }

    /**
     * @return the mean time (in milliseconds) of request handling
     * since {@link #statsReset()} was last called.
     * @see #getRequestTimeTotal()
     * @see #getRequests()
     */
    @ManagedAttribute("mean time spent handling requests (in ms)")
    public double getRequestTimeMean() {
        return _requestTimeStats.getMean();
    }

    /**
     * @return the standard deviation of time (in milliseconds) of request handling
     * since {@link #statsReset()} was last called.
     * @see #getRequestTimeTotal()
     * @see #getRequests()
     */
    @ManagedAttribute("standard deviation for request handling (in ms)")
    public double getRequestTimeStdDev() {
        return _requestTimeStats.getStdDev();
    }

    /**
     * @return the number of dispatches seen by this handler
     * since {@link #statsReset()} was last called, excluding
     * active dispatches
     */
    @ManagedAttribute("number of dispatches")
    public int getDispatched() {
        return (int) _dispatchedStats.getTotal();
    }

    /**
     * @return the number of dispatches currently in this handler
     * since {@link #statsReset()} was last called, including
     * resumed requests
     */
    @ManagedAttribute("number of dispatches currently active")
    public int getDispatchedActive() {
        return (int) _dispatchedStats.getCurrent();
    }

    /**
     * @return the max number of dispatches currently in this handler
     * since {@link #statsReset()} was last called, including
     * resumed requests
     */
    @ManagedAttribute("maximum number of active dispatches being handled")
    public int getDispatchedActiveMax() {
        return (int) _dispatchedStats.getMax();
    }

    /**
     * @return the maximum time (in milliseconds) of request dispatch
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("maximum time spend in dispatch handling")
    public long getDispatchedTimeMax() {
        return _dispatchedTimeStats.getMax();
    }

    /**
     * @return the total time (in milliseconds) of requests handling
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("total time spent in dispatch handling (in ms)")
    public long getDispatchedTimeTotal() {
        return _dispatchedTimeStats.getTotal();
    }

    /**
     * @return the mean time (in milliseconds) of request handling
     * since {@link #statsReset()} was last called.
     * @see #getRequestTimeTotal()
     * @see #getRequests()
     */
    @ManagedAttribute("mean time spent in dispatch handling (in ms)")
    public double getDispatchedTimeMean() {
        return _dispatchedTimeStats.getMean();
    }

    /**
     * @return the standard deviation of time (in milliseconds) of request handling
     * since {@link #statsReset()} was last called.
     * @see #getRequestTimeTotal()
     * @see #getRequests()
     */
    @ManagedAttribute("standard deviation for dispatch handling (in ms)")
    public double getDispatchedTimeStdDev() {
        return _dispatchedTimeStats.getStdDev();
    }

    /**
     * @return the number of requests handled by this handler
     * since {@link #statsReset()} was last called, including
     * resumed requests
     * @see #getAsyncDispatches()
     */
    @ManagedAttribute("total number of async requests")
    public int getAsyncRequests() {
        return (int) _asyncWaitStats.getTotal();
    }

    /**
     * @return the number of requests currently suspended.
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("currently waiting async requests")
    public int getAsyncRequestsWaiting() {
        return (int) _asyncWaitStats.getCurrent();
    }

    /**
     * @return the maximum number of current suspended requests
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("maximum number of waiting async requests")
    public int getAsyncRequestsWaitingMax() {
        return (int) _asyncWaitStats.getMax();
    }

    /**
     * @return the number of requests that have been asynchronously dispatched
     */
    @ManagedAttribute("number of requested that have been asynchronously dispatched")
    public int getAsyncDispatches() {
        return _asyncDispatches.get();
    }

    /**
     * @return the number of requests that expired while suspended.
     * @see #getAsyncDispatches()
     */
    @ManagedAttribute("number of async requests requests that have expired")
    public int getExpires() {
        return _expires.get();
    }

    /**
     * @return the number of responses with a 1xx status returned by this context
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests with 1xx response status")
    public int getResponses1xx() {
        return _responses1xx.get();
    }

    /**
     * @return the number of responses with a 2xx status returned by this context
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests with 2xx response status")
    public int getResponses2xx() {
        return _responses2xx.get();
    }

    /**
     * @return the number of responses with a 3xx status returned by this context
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests with 3xx response status")
    public int getResponses3xx() {
        return _responses3xx.get();
    }

    /**
     * @return the number of responses with a 4xx status returned by this context
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests with 4xx response status")
    public int getResponses4xx() {
        return _responses4xx.get();
    }

    /**
     * @return the number of responses with a 5xx status returned by this context
     * since {@link #statsReset()} was last called.
     */
    @ManagedAttribute("number of requests with 5xx response status")
    public int getResponses5xx() {
        return _responses5xx.get();
    }

    /**
     * @return the milliseconds since the statistics were started with {@link #statsReset()}.
     */
    @ManagedAttribute("time in milliseconds stats have been collected for")
    public long getStatsOnMs() {
        return System.currentTimeMillis() - _statsStartedAt.get();
    }

    /**
     * @return the total bytes of content sent in responses
     */
    @ManagedAttribute("total number of bytes across all responses")
    public long getResponsesBytesTotal() {
        return _responsesTotalBytes.get();
    }

    public String toStatsHTML() {
        StringBuilder sb = new StringBuilder();

        sb.append("<h1>Statistics:</h1>\n");
        sb.append("Statistics gathering started ").append(getStatsOnMs()).append("ms ago").append("<br />\n");

        sb.append("<h2>Requests:</h2>\n");
        sb.append("Total requests: ").append(getRequests()).append("<br />\n");
        sb.append("Active requests: ").append(getRequestsActive()).append("<br />\n");
        sb.append("Max active requests: ").append(getRequestsActiveMax()).append("<br />\n");
        sb.append("Total requests time: ").append(getRequestTimeTotal()).append("<br />\n");
        sb.append("Mean request time: ").append(getRequestTimeMean()).append("<br />\n");
        sb.append("Max request time: ").append(getRequestTimeMax()).append("<br />\n");
        sb.append("Request time standard deviation: ").append(getRequestTimeStdDev()).append("<br />\n");


        sb.append("<h2>Dispatches:</h2>\n");
        sb.append("Total dispatched: ").append(getDispatched()).append("<br />\n");
        sb.append("Active dispatched: ").append(getDispatchedActive()).append("<br />\n");
        sb.append("Max active dispatched: ").append(getDispatchedActiveMax()).append("<br />\n");
        sb.append("Total dispatched time: ").append(getDispatchedTimeTotal()).append("<br />\n");
        sb.append("Mean dispatched time: ").append(getDispatchedTimeMean()).append("<br />\n");
        sb.append("Max dispatched time: ").append(getDispatchedTimeMax()).append("<br />\n");
        sb.append("Dispatched time standard deviation: ").append(getDispatchedTimeStdDev()).append("<br />\n");


        sb.append("Total requests suspended: ").append(getAsyncRequests()).append("<br />\n");
        sb.append("Total requests expired: ").append(getExpires()).append("<br />\n");
        sb.append("Total requests resumed: ").append(getAsyncDispatches()).append("<br />\n");

        sb.append("<h2>Responses:</h2>\n");
        sb.append("1xx responses: ").append(getResponses1xx()).append("<br />\n");
        sb.append("2xx responses: ").append(getResponses2xx()).append("<br />\n");
        sb.append("3xx responses: ").append(getResponses3xx()).append("<br />\n");
        sb.append("4xx responses: ").append(getResponses4xx()).append("<br />\n");
        sb.append("5xx responses: ").append(getResponses5xx()).append("<br />\n");
        sb.append("Bytes sent total: ").append(getResponsesBytesTotal()).append("<br />\n");

        return sb.toString();

    }

    @Override
    public Future<Void> shutdown() {
        FutureCallback shutdown = new FutureCallback(false);
        _shutdown.compareAndSet(null, shutdown);
        shutdown = _shutdown.get();
        if (_dispatchedStats.getCurrent() == 0)
            shutdown.succeeded();
        return shutdown;
    }
}
