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

package Applications.jetty.client;

import Applications.jetty.client.api.Request;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.nio.ByteBuffer;
import java.util.List;

public class RequestNotifier {
    private static final Logger LOG = Log.getLogger(ResponseNotifier.class);

    private final HttpClient client;

    public RequestNotifier(HttpClient client) {
        this.client = client;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyQueued(Request request) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.QueuedListener)
                notifyQueued((Request.QueuedListener) listener, request);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifyQueued(listener, request);
        }
    }

    private void notifyQueued(Request.QueuedListener listener, Request request) {
        try {
            listener.onQueued(request);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyBegin(Request request) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.BeginListener)
                notifyBegin((Request.BeginListener) listener, request);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifyBegin(listener, request);
        }
    }

    private void notifyBegin(Request.BeginListener listener, Request request) {
        try {
            listener.onBegin(request);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyHeaders(Request request) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.HeadersListener)
                notifyHeaders((Request.HeadersListener) listener, request);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifyHeaders(listener, request);
        }
    }

    private void notifyHeaders(Request.HeadersListener listener, Request request) {
        try {
            listener.onHeaders(request);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyCommit(Request request) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.CommitListener)
                notifyCommit((Request.CommitListener) listener, request);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifyCommit(listener, request);
        }
    }

    private void notifyCommit(Request.CommitListener listener, Request request) {
        try {
            listener.onCommit(request);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyContent(Request request, ByteBuffer content) {
        // Slice the buffer to avoid that listeners peek into data they should not look at.
        content = content.slice();
        // Optimized to avoid allocations of iterator instances.
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.ContentListener) {
                // The buffer was sliced, so we always clear it (position=0, limit=capacity)
                // before passing it to the listener that may consume it.
                content.clear();
                notifyContent((Request.ContentListener) listener, request, content);
            }
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            // The buffer was sliced, so we always clear it (position=0, limit=capacity)
            // before passing it to the listener that may consume it.
            content.clear();
            notifyContent(listener, request, content);
        }
    }

    private void notifyContent(Request.ContentListener listener, Request request, ByteBuffer content) {
        try {
            listener.onContent(request, content);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifySuccess(Request request) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.SuccessListener)
                notifySuccess((Request.SuccessListener) listener, request);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifySuccess(listener, request);
        }
    }

    private void notifySuccess(Request.SuccessListener listener, Request request) {
        try {
            listener.onSuccess(request);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void notifyFailure(Request request, Throwable failure) {
        // Optimized to avoid allocations of iterator instances
        List<Request.RequestListener> requestListeners = request.getRequestListeners(null);
        for (int i = 0; i < requestListeners.size(); ++i) {
            Request.RequestListener listener = requestListeners.get(i);
            if (listener instanceof Request.FailureListener)
                notifyFailure((Request.FailureListener) listener, request, failure);
        }
        List<Request.Listener> listeners = client.getRequestListeners();
        for (int i = 0; i < listeners.size(); ++i) {
            Request.Listener listener = listeners.get(i);
            notifyFailure(listener, request, failure);
        }
    }

    private void notifyFailure(Request.FailureListener listener, Request request, Throwable failure) {
        try {
            listener.onFailure(request, failure);
        } catch (Exception x) {
            LOG.info("Exception while notifying listener " + listener, x);
        }
    }
}
