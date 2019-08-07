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

import Applications.jetty.client.api.*;
import Applications.jetty.client.util.FutureResponseListener;
import Applications.jetty.client.util.PathContentProvider;
import Applications.jetty.http.*;
import Applications.jetty.util.Fields;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class HttpRequest implements Request {
    private static final AtomicLong ids = new AtomicLong();

    private final HttpFields headers = new HttpFields();
    private final Fields params = new Fields(true);
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<RequestListener> requestListeners = new ArrayList<>();
    private final List<Response.ResponseListener> responseListeners = new ArrayList<>();
    private final AtomicReference<Throwable> aborted = new AtomicReference<>();
    private final HttpClient client;
    private final long conversation;
    private final String host;
    private final int port;
    private URI uri;
    private String scheme;
    private String path;
    private String query;
    private String method = HttpMethod.GET.asString();
    private HttpVersion version = HttpVersion.HTTP_1_1;
    private long idleTimeout;
    private long timeout;
    private ContentProvider content;
    private boolean followRedirects;

    public HttpRequest(HttpClient client, URI uri) {
        this(client, ids.incrementAndGet(), uri);
    }

    protected HttpRequest(HttpClient client, long conversation, URI uri) {
        this.client = client;
        this.conversation = conversation;
        scheme = uri.getScheme();
        host = client.normalizeHost(uri.getHost());
        port = client.normalizePort(scheme, uri.getPort());
        path = uri.getRawPath();
        query = uri.getRawQuery();
        extractParams(query);
        followRedirects(client.isFollowRedirects());
        idleTimeout = client.getIdleTimeout();
        HttpField acceptEncodingField = client.getAcceptEncodingField();
        if (acceptEncodingField != null)
            headers.put(acceptEncodingField);
    }

    @Override
    public long getConversationID() {
        return conversation;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public Request scheme(String scheme) {
        this.scheme = scheme;
        this.uri = null;
        return this;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public Request method(HttpMethod method) {
        return method(method.asString());
    }

    @Override
    public Request method(String method) {
        this.method = Objects.requireNonNull(method).toUpperCase(Locale.ENGLISH);
        return this;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Request path(String path) {
        URI uri = URI.create(path);
        String rawPath = uri.getRawPath();
        if (uri.isOpaque())
            rawPath = path;
        if (rawPath == null)
            rawPath = "";
        this.path = rawPath;
        String query = uri.getRawQuery();
        if (query != null) {
            this.query = query;
            params.clear();
            extractParams(query);
        }
        if (uri.isAbsolute())
            this.path = buildURI(false).toString();
        this.uri = null;
        return this;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public URI getURI() {
        if (uri != null)
            return uri;
        return uri = buildURI(true);
    }

    @Override
    public HttpVersion getVersion() {
        return version;
    }

    @Override
    public Request version(HttpVersion version) {
        this.version = Objects.requireNonNull(version);
        return this;
    }

    @Override
    public Request param(String name, String value) {
        params.add(name, value);
        this.query = buildQuery();
        this.uri = null;
        return this;
    }

    @Override
    public Fields getParams() {
        return new Fields(params, true);
    }

    @Override
    public String getAgent() {
        return headers.get(HttpHeader.USER_AGENT);
    }

    @Override
    public Request agent(String agent) {
        headers.put(HttpHeader.USER_AGENT, agent);
        return this;
    }

    @Override
    public Request header(String name, String value) {
        if (value == null)
            headers.remove(name);
        else
            headers.add(name, value);
        return this;
    }

    @Override
    public Request header(HttpHeader header, String value) {
        if (value == null)
            headers.remove(header);
        else
            headers.add(header, value);
        return this;
    }

    @Override
    public Request attribute(String name, Object value) {
        attributes.put(name, value);
        return this;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public HttpFields getHeaders() {
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RequestListener> List<T> getRequestListeners(Class<T> type) {
        // This method is invoked often in a request/response conversation,
        // so we avoid allocation if there is no need to filter.
        if (type == null)
            return (List<T>) requestListeners;

        ArrayList<T> result = new ArrayList<>();
        for (RequestListener listener : requestListeners)
            if (type.isInstance(listener))
                result.add((T) listener);
        return result;
    }

    @Override
    public Request listener(Request.Listener listener) {
        this.requestListeners.add(listener);
        return this;
    }

    @Override
    public Request onRequestQueued(final QueuedListener listener) {
        this.requestListeners.add(new QueuedListener() {
            @Override
            public void onQueued(Request request) {
                listener.onQueued(request);
            }
        });
        return this;
    }

    @Override
    public Request onRequestBegin(final BeginListener listener) {
        this.requestListeners.add(new BeginListener() {
            @Override
            public void onBegin(Request request) {
                listener.onBegin(request);
            }
        });
        return this;
    }

    @Override
    public Request onRequestHeaders(final HeadersListener listener) {
        this.requestListeners.add(new HeadersListener() {
            @Override
            public void onHeaders(Request request) {
                listener.onHeaders(request);
            }
        });
        return this;
    }

    @Override
    public Request onRequestCommit(final CommitListener listener) {
        this.requestListeners.add(new CommitListener() {
            @Override
            public void onCommit(Request request) {
                listener.onCommit(request);
            }
        });
        return this;
    }

    @Override
    public Request onRequestContent(final ContentListener listener) {
        this.requestListeners.add(new ContentListener() {
            @Override
            public void onContent(Request request, ByteBuffer content) {
                listener.onContent(request, content);
            }
        });
        return this;
    }

    @Override
    public Request onRequestSuccess(final SuccessListener listener) {
        this.requestListeners.add(new SuccessListener() {
            @Override
            public void onSuccess(Request request) {
                listener.onSuccess(request);
            }
        });
        return this;
    }

    @Override
    public Request onRequestFailure(final FailureListener listener) {
        this.requestListeners.add(new FailureListener() {
            @Override
            public void onFailure(Request request, Throwable failure) {
                listener.onFailure(request, failure);
            }
        });
        return this;
    }

    @Override
    public Request onResponseBegin(final Response.BeginListener listener) {
        this.responseListeners.add(new Response.BeginListener() {
            @Override
            public void onBegin(Response response) {
                listener.onBegin(response);
            }
        });
        return this;
    }

    @Override
    public Request onResponseHeader(final Response.HeaderListener listener) {
        this.responseListeners.add(new Response.HeaderListener() {
            @Override
            public boolean onHeader(Response response, HttpField field) {
                return listener.onHeader(response, field);
            }
        });
        return this;
    }

    @Override
    public Request onResponseHeaders(final Response.HeadersListener listener) {
        this.responseListeners.add(new Response.HeadersListener() {
            @Override
            public void onHeaders(Response response) {
                listener.onHeaders(response);
            }
        });
        return this;
    }

    @Override
    public Request onResponseContent(final Response.ContentListener listener) {
        this.responseListeners.add(new Response.ContentListener() {
            @Override
            public void onContent(Response response, ByteBuffer content) {
                listener.onContent(response, content);
            }
        });
        return this;
    }

    @Override
    public Request onResponseSuccess(final Response.SuccessListener listener) {
        this.responseListeners.add(new Response.SuccessListener() {
            @Override
            public void onSuccess(Response response) {
                listener.onSuccess(response);
            }
        });
        return this;
    }

    @Override
    public Request onResponseFailure(final Response.FailureListener listener) {
        this.responseListeners.add(new Response.FailureListener() {
            @Override
            public void onFailure(Response response, Throwable failure) {
                listener.onFailure(response, failure);
            }
        });
        return this;
    }

    @Override
    public Request onComplete(final Response.CompleteListener listener) {
        this.responseListeners.add(new Response.CompleteListener() {
            @Override
            public void onComplete(Result result) {
                listener.onComplete(result);
            }
        });
        return this;
    }

    @Override
    public ContentProvider getContent() {
        return content;
    }

    @Override
    public Request content(ContentProvider content) {
        return content(content, null);
    }

    @Override
    public Request content(ContentProvider content, String contentType) {
        if (contentType != null)
            header(HttpHeader.CONTENT_TYPE, contentType);
        this.content = content;
        return this;
    }

    @Override
    public Request file(Path file) throws IOException {
        return file(file, "application/octet-stream");
    }

    @Override
    public Request file(Path file, String contentType) throws IOException {
        if (contentType != null)
            header(HttpHeader.CONTENT_TYPE, contentType);
        return content(new PathContentProvider(file));
    }

    @Override
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    @Override
    public Request followRedirects(boolean follow) {
        this.followRedirects = follow;
        return this;
    }

    @Override
    public long getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public Request idleTimeout(long timeout, TimeUnit unit) {
        this.idleTimeout = unit.toMillis(timeout);
        return this;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public Request timeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
        return this;
    }

    @Override
    public ContentResponse send() throws InterruptedException, TimeoutException, ExecutionException {
        FutureResponseListener listener = new FutureResponseListener(this);
        send(this, listener);

        try {
            long timeout = getTimeout();
            if (timeout <= 0)
                return listener.get();

            return listener.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException x) {
            // Differently from the Future, the semantic of this method is that if
            // the send() is interrupted or times out, we abort the request.
            abort(x);
            throw x;
        }
    }

    @Override
    public void send(Response.CompleteListener listener) {
        if (getTimeout() > 0) {
            TimeoutCompleteListener timeoutListener = new TimeoutCompleteListener(this);
            timeoutListener.schedule(client.getScheduler());
            responseListeners.add(timeoutListener);
        }
        send(this, listener);
    }

    private void send(Request request, Response.CompleteListener listener) {
        if (listener != null)
            responseListeners.add(listener);
        client.send(request, responseListeners);
    }

    @Override
    public boolean abort(Throwable cause) {
        if (aborted.compareAndSet(null, Objects.requireNonNull(cause))) {
            // The conversation may be null if it is already completed
            HttpConversation conversation = client.getConversation(getConversationID(), false);
            return conversation != null && conversation.abort(cause);
        }
        return false;
    }

    @Override
    public Throwable getAbortCause() {
        return aborted.get();
    }

    private String buildQuery() {
        StringBuilder result = new StringBuilder();
        for (Iterator<Fields.Field> iterator = params.iterator(); iterator.hasNext(); ) {
            Fields.Field field = iterator.next();
            List<String> values = field.getValues();
            for (int i = 0; i < values.size(); ++i) {
                if (i > 0)
                    result.append("&");
                result.append(field.getName()).append("=");
                result.append(urlEncode(values.get(i)));
            }
            if (iterator.hasNext())
                result.append("&");
        }
        return result.toString();
    }

    private String urlEncode(String value) {
        String encoding = "UTF-8";
        try {
            return URLEncoder.encode(value, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(encoding);
        }
    }

    private void extractParams(String query) {
        if (query != null) {
            for (String nameValue : query.split("&")) {
                String[] parts = nameValue.split("=");
                if (parts.length > 0) {
                    String name = parts[0];
                    if (name.trim().length() == 0)
                        continue;
                    param(name, parts.length < 2 ? "" : urlDecode(parts[1]));
                }
            }
        }
    }

    private String urlDecode(String value) {
        String charset = "UTF-8";
        try {
            return URLDecoder.decode(value, charset);
        } catch (UnsupportedEncodingException x) {
            throw new UnsupportedCharsetException(charset);
        }
    }

    private URI buildURI(boolean withQuery) {
        String path = getPath();
        String query = getQuery();
        if (query != null && withQuery)
            path += "?" + query;
        URI result = URI.create(path);
        if (!result.isAbsolute() && !result.isOpaque())
            result = URI.create(new Origin(getScheme(), getHost(), getPort()).asString() + path);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s[%s %s %s]@%x", HttpRequest.class.getSimpleName(), getMethod(), getPath(), getVersion(), hashCode());
    }
}
