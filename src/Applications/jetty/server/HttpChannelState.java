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

import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.server.handler.ContextHandler.Context;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.thread.Scheduler;

import javax.servlet.AsyncListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of AsyncContext interface that holds the state of request-response cycle.
 * <p>
 * <table>
 * <tr><th>STATE</th><th colspan=6>ACTION</th></tr>
 * <tr><th></th>                           <th>handling()</th>  <th>startAsync()</th><th>unhandle()</th>  <th>dispatch()</th>   <th>complete()</th>      <th>completed()</th></tr>
 * <tr><th align=right>IDLE:</th>          <td>DISPATCHED</td>  <td></td>            <td></td>            <td></td>             <td>COMPLETECALLED??</td><td></td></tr>
 * <tr><th align=right>DISPATCHED:</th>    <td></td>            <td>ASYNCSTARTED</td><td>COMPLETING</td>  <td></td>             <td></td>                <td></td></tr>
 * <tr><th align=right>ASYNCSTARTED:</th>  <td></td>            <td></td>            <td>ASYNCWAIT</td>   <td>REDISPATCHING</td><td>COMPLETECALLED</td>  <td></td></tr>
 * <tr><th align=right>REDISPATCHING:</th> <td></td>            <td></td>            <td>REDISPATCHED</td><td></td>             <td></td>                <td></td></tr>
 * <tr><th align=right>ASYNCWAIT:</th>     <td></td>            <td></td>            <td></td>            <td>REDISPATCH</td>   <td>COMPLETECALLED</td>  <td></td></tr>
 * <tr><th align=right>REDISPATCH:</th>    <td>REDISPATCHED</td><td></td>            <td></td>            <td></td>             <td></td>                <td></td></tr>
 * <tr><th align=right>REDISPATCHED:</th>  <td></td>            <td>ASYNCSTARTED</td><td>COMPLETING</td>  <td></td>             <td></td>                <td></td></tr>
 * <tr><th align=right>COMPLETECALLED:</th><td>COMPLETING</td>  <td></td>            <td>COMPLETING</td>  <td></td>             <td></td>                <td></td></tr>
 * <tr><th align=right>COMPLETING:</th>    <td>COMPLETING</td>  <td></td>            <td></td>            <td></td>             <td></td>                <td>COMPLETED</td></tr>
 * <tr><th align=right>COMPLETED:</th>     <td></td>            <td></td>            <td></td>            <td></td>             <td></td>                <td></td></tr>
 * </table>
 */
public class HttpChannelState {
    private static final Logger LOG = Log.getLogger(HttpChannelState.class);

    private final static long DEFAULT_TIMEOUT = 30000L;

    public enum State {
        IDLE,          // Idle request
        DISPATCHED,    // Request dispatched to filter/servlet
        ASYNCWAIT,     // Suspended and parked
        ASYNCIO,       // Has been dispatched for async IO
        COMPLETING,    // Request is completable
        COMPLETED      // Request is complete
    }

    public enum Action {
        REQUEST_DISPATCH, // handle a normal request dispatch  
        ASYNC_DISPATCH,   // handle an async request dispatch
        ASYNC_EXPIRED,    // handle an async timeout
        WRITE_CALLBACK,   // handle an IO write callback
        READ_CALLBACK,    // handle an IO read callback
        WAIT,             // Wait for further events 
        COMPLETE          // Complete the channel
    }

    public enum Async {
        STARTED,
        DISPATCH,
        COMPLETE,
        EXPIRING,
        EXPIRED
    }

    private final boolean DEBUG = LOG.isDebugEnabled();
    private final HttpChannel<?> _channel;

    private List<AsyncListener> _asyncListeners;
    private State _state;
    private Async _async;
    private boolean _initial;
    private boolean _asyncRead;
    private boolean _asyncWrite;
    private long _timeoutMs = DEFAULT_TIMEOUT;
    private AsyncContextEvent _event;

    protected HttpChannelState(HttpChannel<?> channel) {
        _channel = channel;
        _state = State.IDLE;
        _async = null;
        _initial = true;
    }

    public State getState() {
        synchronized (this) {
            return _state;
        }
    }

    public void addListener(AsyncListener listener) {
        synchronized (this) {
            if (_asyncListeners == null)
                _asyncListeners = new ArrayList<>();
            _asyncListeners.add(listener);
        }
    }

    public void setTimeout(long ms) {
        synchronized (this) {
            _timeoutMs = ms;
        }
    }

    public long getTimeout() {
        synchronized (this) {
            return _timeoutMs;
        }
    }

    public AsyncContextEvent getAsyncContextEvent() {
        synchronized (this) {
            return _event;
        }
    }

    @Override
    public String toString() {
        synchronized (this) {
            return String.format("%s@%x{s=%s i=%b a=%s}", getClass().getSimpleName(), hashCode(), _state, _initial, _async);
        }
    }

    public String getStatusString() {
        synchronized (this) {
            return String.format("s=%s i=%b a=%s", _state, _initial, _async);
        }
    }

    /**
     * @return Next handling of the request should proceed
     */
    protected Action handling() {
        synchronized (this) {
            if (DEBUG)
                LOG.debug("{} handling {}", this, _state);
            switch (_state) {
                case IDLE:
                    _initial = true;
                    _state = State.DISPATCHED;
                    return Action.REQUEST_DISPATCH;

                case COMPLETING:
                    return Action.COMPLETE;

                case COMPLETED:
                    return Action.WAIT;

                case ASYNCWAIT:
                    if (_asyncRead) {
                        _state = State.ASYNCIO;
                        _asyncRead = false;
                        return Action.READ_CALLBACK;
                    }
                    if (_asyncWrite) {
                        _state = State.ASYNCIO;
                        _asyncWrite = false;
                        return Action.WRITE_CALLBACK;
                    }

                    if (_async != null) {
                        Async async = _async;
                        switch (async) {
                            case COMPLETE:
                                _state = State.COMPLETING;
                                return Action.COMPLETE;
                            case DISPATCH:
                                _state = State.DISPATCHED;
                                _async = null;
                                return Action.ASYNC_DISPATCH;
                            case EXPIRING:
                                break;
                            case EXPIRED:
                                _state = State.DISPATCHED;
                                _async = null;
                                return Action.ASYNC_EXPIRED;
                            case STARTED:
                                if (DEBUG)
                                    LOG.debug("TODO Fix this double dispatch", new IllegalStateException(this
                                            .getStatusString()));
                                return Action.WAIT;
                        }
                    }

                    return Action.WAIT;

                default:
                    throw new IllegalStateException(this.getStatusString());
            }
        }
    }

    public void startAsync(AsyncContextEvent event) {
        final List<AsyncListener> lastAsyncListeners;

        synchronized (this) {
            if (_state != State.DISPATCHED || _async != null)
                throw new IllegalStateException(this.getStatusString());

            _async = Async.STARTED;
            _event = event;
            lastAsyncListeners = _asyncListeners;
            _asyncListeners = null;
        }

        if (lastAsyncListeners != null) {
            for (AsyncListener listener : lastAsyncListeners) {
                try {
                    listener.onStartAsync(event);
                } catch (Exception e) {
                    LOG.warn(e);
                }
            }
        }
    }

    protected void error(Throwable th) {
        synchronized (this) {
            if (_event != null)
                _event.setThrowable(th);
        }
    }

    /**
     * Signal that the HttpConnection has finished handling the request.
     * For blocking connectors, this call may block if the request has
     * been suspended (startAsync called).
     *
     * @return next actions
     * be handled again (eg because of a resume that happened before unhandle was called)
     */
    protected Action unhandle() {
        synchronized (this) {
            if (DEBUG)
                LOG.debug("{} unhandle {}", this, _state);

            switch (_state) {
                case DISPATCHED:
                case ASYNCIO:
                    break;
                default:
                    throw new IllegalStateException(this.getStatusString());
            }

            if (_asyncRead) {
                _state = State.ASYNCIO;
                _asyncRead = false;
                return Action.READ_CALLBACK;
            }

            if (_asyncWrite) {
                _asyncWrite = false;
                _state = State.ASYNCIO;
                return Action.WRITE_CALLBACK;
            }

            if (_async != null) {
                _initial = false;
                switch (_async) {
                    case COMPLETE:
                        _state = State.COMPLETING;
                        _async = null;
                        return Action.COMPLETE;
                    case DISPATCH:
                        _state = State.DISPATCHED;
                        _async = null;
                        return Action.ASYNC_DISPATCH;
                    case EXPIRED:
                        _state = State.DISPATCHED;
                        _async = null;
                        return Action.ASYNC_EXPIRED;
                    case EXPIRING:
                    case STARTED:
                        scheduleTimeout();
                        _state = State.ASYNCWAIT;
                        return Action.WAIT;
                }
            }

            _state = State.COMPLETING;
            return Action.COMPLETE;
        }
    }

    public void dispatch(ServletContext context, String path) {
        boolean dispatch;
        synchronized (this) {
            if (_async != Async.STARTED && _async != Async.EXPIRING)
                throw new IllegalStateException("AsyncContext#dispath " + this.getStatusString());
            _async = Async.DISPATCH;

            if (context != null)
                _event.setDispatchContext(context);
            if (path != null)
                _event.setDispatchPath(path);

            switch (_state) {
                case DISPATCHED:
                case ASYNCIO:
                    dispatch = false;
                    break;
                default:
                    dispatch = true;
                    break;
            }
        }

        cancelTimeout();
        if (dispatch)
            scheduleDispatch();
    }

    protected void expired() {
        final List<AsyncListener> aListeners;
        AsyncContextEvent event;
        synchronized (this) {
            if (_async != Async.STARTED)
                return;
            _async = Async.EXPIRING;
            event = _event;
            aListeners = _asyncListeners;
        }

        if (aListeners != null) {
            for (AsyncListener listener : aListeners) {
                try {
                    listener.onTimeout(event);
                } catch (Exception e) {
                    LOG.debug(e);
                    event.setThrowable(e);
                    _channel.getRequest().setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
                    break;
                }
            }
        }

        boolean dispatch = false;
        synchronized (this) {
            if (_async == Async.EXPIRING) {
                _async = Async.EXPIRED;
                if (_state == State.ASYNCWAIT)
                    dispatch = true;
            }
        }

        if (dispatch)
            scheduleDispatch();
    }

    public void complete() {
        // just like resume, except don't set _dispatched=true;
        boolean handle;
        synchronized (this) {
            if (_async != Async.STARTED && _async != Async.EXPIRING)
                throw new IllegalStateException(this.getStatusString());
            _async = Async.COMPLETE;
            handle = _state == State.ASYNCWAIT;
        }

        cancelTimeout();
        if (handle) {
            ContextHandler handler = getContextHandler();
            if (handler != null)
                handler.handle(_channel);
            else
                _channel.handle();
        }
    }

    public void errorComplete() {
        synchronized (this) {
            _async = Async.COMPLETE;
            _event.setDispatchContext(null);
            _event.setDispatchPath(null);
        }

        cancelTimeout();
    }

    protected void completed() {
        final List<AsyncListener> aListeners;
        final AsyncContextEvent event;
        synchronized (this) {
            switch (_state) {
                case COMPLETING:
                    _state = State.COMPLETED;
                    aListeners = _asyncListeners;
                    event = _event;
                    break;

                default:
                    throw new IllegalStateException(this.getStatusString());
            }
        }

        if (event != null) {
            if (aListeners != null) {
                if (event.getThrowable() != null) {
                    event.getSuppliedRequest().setAttribute(RequestDispatcher.ERROR_EXCEPTION, event.getThrowable());
                    event.getSuppliedRequest().setAttribute(RequestDispatcher.ERROR_MESSAGE, event.getThrowable().getMessage());
                }

                for (AsyncListener listener : aListeners) {
                    try {
                        if (event.getThrowable() != null)
                            listener.onError(event);
                        else
                            listener.onComplete(event);
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                }
            }

            event.completed();
        }
    }

    protected void recycle() {
        synchronized (this) {
            switch (_state) {
                case DISPATCHED:
                case ASYNCIO:
                    throw new IllegalStateException(getStatusString());
                default:
                    break;
            }
            _asyncListeners = null;
            _state = State.IDLE;
            _async = null;
            _initial = true;
            _asyncRead = false;
            _asyncWrite = false;
            _timeoutMs = DEFAULT_TIMEOUT;
            cancelTimeout();
            _event = null;
        }
    }

    protected void scheduleDispatch() {
        _channel.execute(_channel);
    }

    protected void scheduleTimeout() {
        Scheduler scheduler = _channel.getScheduler();
        if (scheduler != null && _timeoutMs > 0)
            _event.setTimeoutTask(scheduler.schedule(new AsyncTimeout(), _timeoutMs, TimeUnit.MILLISECONDS));
    }

    protected void cancelTimeout() {
        final AsyncContextEvent event;
        synchronized (this) {
            event = _event;
        }
        if (event != null)
            event.cancelTimeoutTask();
    }

    public boolean isExpired() {
        synchronized (this) {
            return _async == Async.EXPIRED;
        }
    }

    public boolean isInitial() {
        synchronized (this) {
            return _initial;
        }
    }

    public boolean isSuspended() {
        synchronized (this) {
            return _state == State.ASYNCWAIT || _state == State.DISPATCHED && _async == Async.STARTED;
        }
    }

    boolean isCompleting() {
        synchronized (this) {
            return _state == State.COMPLETING;
        }
    }

    boolean isCompleted() {
        synchronized (this) {
            return _state == State.COMPLETED;
        }
    }

    public boolean isAsyncStarted() {
        synchronized (this) {
            if (_state == State.DISPATCHED)
                return _async != null;
            return _async == Async.STARTED || _async == Async.EXPIRING;
        }
    }

    public boolean isAsync() {
        synchronized (this) {
            return !_initial || _async != null;
        }
    }

    public Request getBaseRequest() {
        return _channel.getRequest();
    }

    public HttpChannel<?> getHttpChannel() {
        return _channel;
    }

    public ContextHandler getContextHandler() {
        final AsyncContextEvent event;
        synchronized (this) {
            event = _event;
        }

        if (event != null) {
            Context context = ((Context) event.getServletContext());
            if (context != null)
                return context.getContextHandler();
        }
        return null;
    }

    public ServletResponse getServletResponse() {
        final AsyncContextEvent event;
        synchronized (this) {
            event = _event;
        }
        if (event != null && event.getSuppliedResponse() != null)
            return event.getSuppliedResponse();
        return _channel.getResponse();
    }

    public Object getAttribute(String name) {
        return _channel.getRequest().getAttribute(name);
    }

    public void removeAttribute(String name) {
        _channel.getRequest().removeAttribute(name);
    }

    public void setAttribute(String name, Object attribute) {
        _channel.getRequest().setAttribute(name, attribute);
    }

    public void onReadPossible() {
        boolean handle;

        synchronized (this) {
            _asyncRead = true;
            handle = _state == State.ASYNCWAIT;
        }

        if (handle)
            _channel.execute(_channel);
    }

    public void onWritePossible() {
        boolean handle;

        synchronized (this) {
            _asyncWrite = true;
            handle = _state == State.ASYNCWAIT;
        }

        if (handle)
            _channel.execute(_channel);
    }

    public class AsyncTimeout implements Runnable {
        @Override
        public void run() {
            HttpChannelState.this.expired();
        }
    }

}
