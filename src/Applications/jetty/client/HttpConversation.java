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

import Applications.jetty.client.api.Response;
import Applications.jetty.util.AttributesMap;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class HttpConversation extends AttributesMap {
    private final Deque<HttpExchange> exchanges = new ConcurrentLinkedDeque<>();
    private final HttpClient client;
    private final long id;
    private volatile boolean complete;
    private volatile List<Response.ResponseListener> listeners;

    public HttpConversation(HttpClient client, long id) {
        this.client = client;
        this.id = id;
    }

    public long getID() {
        return id;
    }

    public Deque<HttpExchange> getExchanges() {
        return exchanges;
    }

    /**
     * Returns the list of response listeners that needs to be notified of response events.
     * This list changes as the conversation proceeds, as follows:
     * <ol>
     * <li>
     * request R1 send => conversation.updateResponseListeners(null)
     * <ul>
     * <li>exchanges in conversation: E1</li>
     * <li>listeners to be notified: E1.listeners</li>
     * </ul>
     * </li>
     * <li>
     * response R1 arrived, 401 => conversation.updateResponseListeners(AuthenticationProtocolHandler.listener)
     * <ul>
     * <li>exchanges in conversation: E1</li>
     * <li>listeners to be notified: AuthenticationProtocolHandler.listener</li>
     * </ul>
     * </li>
     * <li>
     * request R2 send => conversation.updateResponseListeners(null)
     * <ul>
     * <li>exchanges in conversation: E1 + E2</li>
     * <li>listeners to be notified: E2.listeners + E1.listeners</li>
     * </ul>
     * </li>
     * <li>
     * response R2 arrived, 302 => conversation.updateResponseListeners(RedirectProtocolHandler.listener)
     * <ul>
     * <li>exchanges in conversation: E1 + E2</li>
     * <li>listeners to be notified: E2.listeners + RedirectProtocolHandler.listener</li>
     * </ul>
     * </li>
     * <li>
     * request R3 send => conversation.updateResponseListeners(null)
     * <ul>
     * <li>exchanges in conversation: E1 + E2 + E3</li>
     * <li>listeners to be notified: E3.listeners + E1.listeners</li>
     * </ul>
     * </li>
     * <li>
     * response R3 arrived, 200 => conversation.updateResponseListeners(null)
     * <ul>
     * <li>exchanges in conversation: E1 + E2 + E3</li>
     * <li>listeners to be notified: E3.listeners + E1.listeners</li>
     * </ul>
     * </li>
     * </ol>
     * Basically the override conversation listener replaces the first exchange response listener,
     * and we also notify the last exchange response listeners (if it's not also the first).
     * <p>
     * This scheme allows for protocol handlers to not worry about other protocol handlers, or to worry
     * too much about notifying the first exchange response listeners, but still allowing a protocol
     * handler to perform completion activities while another protocol handler performs new ones (as an
     * example, the {@link AuthenticationProtocolHandler} stores the successful authentication credentials
     * while the {@link RedirectProtocolHandler} performs a redirect).
     *
     * @return the list of response listeners that needs to be notified of response events
     */
    public List<Response.ResponseListener> getResponseListeners() {
        return listeners;
    }

    /**
     * Requests to update the response listener, eventually using the given override response listener,
     * that must be notified instead of the first exchange response listeners.
     * This works in conjunction with {@link #getResponseListeners()}, returning the appropriate response
     * listeners that needs to be notified of response events.
     *
     * @param overrideListener the override response listener
     */
    public void updateResponseListeners(Response.ResponseListener overrideListener) {
        // If we have no override listener, then the
        // conversation may be completed at a later time
        complete = overrideListener == null;

        // Create a new instance to avoid that iterating over the listeners
        // will notify a listener that may send a new request and trigger
        // another call to this method which will build different listeners
        // which may be iterated over when the iteration continues.
        List<Response.ResponseListener> listeners = new ArrayList<>();
        HttpExchange firstExchange = exchanges.peekFirst();
        HttpExchange lastExchange = exchanges.peekLast();
        if (firstExchange == lastExchange) {
            if (overrideListener != null)
                listeners.add(overrideListener);
            else
                listeners.addAll(firstExchange.getResponseListeners());
        } else {
            // Order is important, we want to notify the last exchange first
            listeners.addAll(lastExchange.getResponseListeners());
            if (overrideListener != null)
                listeners.add(overrideListener);
            else
                listeners.addAll(firstExchange.getResponseListeners());
        }
        this.listeners = listeners;
    }

    public void complete() {
        if (complete)
            client.removeConversation(this);
    }

    public boolean abort(Throwable cause) {
        HttpExchange exchange = exchanges.peekLast();
        return exchange.abort(cause);
    }

    @Override
    public String toString() {
        return String.format("%s[%d]", HttpConversation.class.getSimpleName(), id);
    }
}
