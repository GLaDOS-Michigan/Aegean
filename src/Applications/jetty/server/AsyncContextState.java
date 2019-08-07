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

import javax.servlet.*;
import java.io.IOException;


public class AsyncContextState implements AsyncContext {
    volatile HttpChannelState _state;

    public AsyncContextState(HttpChannelState state) {
        _state = state;
    }

    HttpChannelState state() {
        HttpChannelState state = _state;
        if (state == null)
            throw new IllegalStateException("AsyncContext completed");
        return state;
    }

    @Override
    public void addListener(final AsyncListener listener, final ServletRequest request, final ServletResponse response) {
        AsyncListener wrap = new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                listener.onTimeout(new AsyncEvent(event.getAsyncContext(), request, response, event.getThrowable()));
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                listener.onStartAsync(new AsyncEvent(event.getAsyncContext(), request, response, event.getThrowable()));
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                listener.onComplete(new AsyncEvent(event.getAsyncContext(), request, response, event.getThrowable()));
            }

            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                listener.onComplete(new AsyncEvent(event.getAsyncContext(), request, response, event.getThrowable()));
            }
        };
        state().addListener(wrap);
    }

    @Override
    public void addListener(AsyncListener listener) {
        state().addListener(listener);
    }

    @Override
    public void complete() {
        state().complete();
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
        ContextHandler contextHandler = state().getContextHandler();
        if (contextHandler != null)
            return contextHandler.getServletContext().createListener(clazz);
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void dispatch() {
        state().dispatch(null, null);
    }

    @Override
    public void dispatch(String path) {
        state().dispatch(null, path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        state().dispatch(context, path);
    }

    @Override
    public ServletRequest getRequest() {
        return state().getAsyncContextEvent().getSuppliedRequest();
    }

    @Override
    public ServletResponse getResponse() {
        return state().getAsyncContextEvent().getSuppliedResponse();
    }

    @Override
    public long getTimeout() {
        return state().getTimeout();
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        HttpChannel<?> channel = state().getHttpChannel();
        return channel.getRequest() == getRequest() && channel.getResponse() == getResponse();
    }

    @Override
    public void setTimeout(long arg0) {
        state().setTimeout(arg0);
    }

    @Override
    public void start(final Runnable task) {
        state().getHttpChannel().execute(new Runnable() {
            @Override
            public void run() {
                state().getAsyncContextEvent().getContext().getContextHandler().handle(task);
            }
        });
    }

    public void reset() {
        _state = null;
    }

    public HttpChannelState getHttpChannelState() {
        return state();
    }


}
