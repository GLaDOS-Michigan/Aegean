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
import Applications.jetty.client.api.Response;
import Applications.jetty.client.api.Result;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HttpExchange {
    private static final Logger LOG = Log.getLogger(HttpExchange.class);

    private final AtomicBoolean requestComplete = new AtomicBoolean();
    private final AtomicBoolean responseComplete = new AtomicBoolean();
    private final AtomicInteger complete = new AtomicInteger();
    private final AtomicReference<HttpChannel> channel = new AtomicReference<>();
    private final HttpConversation conversation;
    private final HttpDestination destination;
    private final Request request;
    private final List<Response.ResponseListener> listeners;
    private final HttpResponse response;
    private volatile Throwable requestFailure;
    private volatile Throwable responseFailure;


    public HttpExchange(HttpConversation conversation, HttpDestination destination, Request request, List<Response.ResponseListener> listeners) {
        this.conversation = conversation;
        this.destination = destination;
        this.request = request;
        this.listeners = listeners;
        this.response = new HttpResponse(request, listeners);
        conversation.getExchanges().offer(this);
        conversation.updateResponseListeners(null);
    }

    public HttpConversation getConversation() {
        return conversation;
    }

    public Request getRequest() {
        return request;
    }

    public Throwable getRequestFailure() {
        return requestFailure;
    }

    public List<Response.ResponseListener> getResponseListeners() {
        return listeners;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public Throwable getResponseFailure() {
        return responseFailure;
    }

    public void associate(HttpChannel channel) {
        if (!this.channel.compareAndSet(null, channel))
            throw new IllegalStateException();
    }

    public void disassociate(HttpChannel channel) {
        if (!this.channel.compareAndSet(channel, null))
            throw new IllegalStateException();
    }

    public boolean requestComplete() {
        return requestComplete.compareAndSet(false, true);
    }

    public boolean responseComplete() {
        return responseComplete.compareAndSet(false, true);
    }

    public Result terminateRequest(Throwable failure) {
        int requestSuccess = 0b0011;
        int requestFailure = 0b0001;
        return terminate(failure == null ? requestSuccess : requestFailure, failure);
    }

    public Result terminateResponse(Throwable failure) {
        if (failure == null) {
            int responseSuccess = 0b1100;
            return terminate(responseSuccess, failure);
        } else {
            proceed(false);
            int responseFailure = 0b0100;
            return terminate(responseFailure, failure);
        }
    }

    /**
     * This method needs to atomically compute whether this exchange is completed,
     * that is both request and responses are completed (either with a success or
     * a failure).
     * <p>
     * Furthermore, this method needs to atomically compute whether the exchange
     * has completed successfully (both request and response are successful) or not.
     * <p>
     * To do this, we use 2 bits for the request (one to indicate completion, one
     * to indicate success), and similarly for the response.
     * By using {@link AtomicInteger} to atomically sum these codes we can know
     * whether the exchange is completed and whether is successful.
     *
     * @return the {@link Result} - if any - associated with the status
     */
    private Result terminate(int code, Throwable failure) {
        int current;
        while (true) {
            current = complete.get();
            boolean updateable = (current & code) == 0;
            if (updateable) {
                int candidate = current | code;
                if (!complete.compareAndSet(current, candidate))
                    continue;
                current = candidate;
                if ((code & 0b01) == 0b01)
                    requestFailure = failure;
                else
                    responseFailure = failure;
                LOG.debug("{} updated", this);
            }
            break;
        }

        int terminated = 0b0101;
        if ((current & terminated) == terminated) {
            // Request and response terminated
            LOG.debug("{} terminated", this);
            conversation.complete();
            return new Result(getRequest(), getRequestFailure(), getResponse(), getResponseFailure());
        }

        return null;
    }

    public boolean abort(Throwable cause) {
        if (destination.remove(this)) {
            destination.abort(this, cause);
            LOG.debug("Aborted while queued {}: {}", this, cause);
            return true;
        } else {
            HttpChannel channel = this.channel.get();
            // If there is no channel, this exchange is already completed
            if (channel == null)
                return false;

            boolean aborted = channel.abort(cause);
            LOG.debug("Aborted while active ({}) {}: {}", aborted, this, cause);
            return aborted;
        }
    }

    public void resetResponse(boolean success) {
        responseComplete.set(false);
        int responseSuccess = 0b1100;
        int responseFailure = 0b0100;
        int code = success ? responseSuccess : responseFailure;
        complete.addAndGet(-code);
    }

    public void proceed(boolean proceed) {
        HttpChannel channel = this.channel.get();
        if (channel != null)
            channel.proceed(this, proceed);
    }

    private String toString(int code) {
        String padding = "0000";
        String status = Integer.toBinaryString(code);
        return String.format("%s@%x status=%s%s",
                HttpExchange.class.getSimpleName(),
                hashCode(),
                padding.substring(status.length()),
                status);
    }

    @Override
    public String toString() {
        return toString(complete.get());
    }
}
