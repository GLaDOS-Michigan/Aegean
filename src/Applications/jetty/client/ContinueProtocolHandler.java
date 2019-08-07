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
import Applications.jetty.client.util.BufferingResponseListener;
import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpHeaderValue;

import java.util.List;

public class ContinueProtocolHandler implements ProtocolHandler {
    private static final String ATTRIBUTE = ContinueProtocolHandler.class.getName() + ".100continue";

    private final HttpClient client;
    private final ResponseNotifier notifier;

    public ContinueProtocolHandler(HttpClient client) {
        this.client = client;
        this.notifier = new ResponseNotifier(client);
    }

    @Override
    public boolean accept(Request request, Response response) {
        boolean expect100 = request.getHeaders().contains(HttpHeader.EXPECT, HttpHeaderValue.CONTINUE.asString());
        HttpConversation conversation = client.getConversation(request.getConversationID(), false);
        boolean handled100 = conversation != null && conversation.getAttribute(ATTRIBUTE) != null;
        return expect100 && !handled100;
    }

    @Override
    public Response.Listener getResponseListener() {
        // Return new instances every time to keep track of the response content
        return new ContinueListener();
    }

    protected class ContinueListener extends BufferingResponseListener {
        @Override
        public void onSuccess(Response response) {
            // Handling of success must be done here and not from onComplete(),
            // since the onComplete() is not invoked because the request is not completed yet.

            HttpConversation conversation = client.getConversation(response.getConversationID(), false);
            // Mark the 100 Continue response as handled
            conversation.setAttribute(ATTRIBUTE, Boolean.TRUE);

            // Reset the conversation listeners, since we are going to receive another response code
            conversation.updateResponseListeners(null);

            HttpExchange exchange = conversation.getExchanges().peekLast();
            assert exchange.getResponse() == response;
            switch (response.getStatus()) {
                case 100: {
                    // All good, continue
                    exchange.resetResponse(true);
                    exchange.proceed(true);
                    break;
                }
                default: {
                    // Server either does not support 100 Continue,
                    // or it does and wants to refuse the request content,
                    // or we got some other HTTP status code like a redirect.
                    List<Response.ResponseListener> listeners = exchange.getResponseListeners();
                    HttpContentResponse contentResponse = new HttpContentResponse(response, getContent(), getEncoding());
                    notifier.forwardSuccess(listeners, contentResponse);
                    exchange.proceed(false);
                    break;
                }
            }
        }

        @Override
        public void onFailure(Response response, Throwable failure) {
            HttpConversation conversation = client.getConversation(response.getConversationID(), false);
            // Mark the 100 Continue response as handled
            conversation.setAttribute(ATTRIBUTE, Boolean.TRUE);
            // Reset the conversation listeners to allow the conversation to be completed
            conversation.updateResponseListeners(null);

            HttpExchange exchange = conversation.getExchanges().peekLast();
            assert exchange.getResponse() == response;
            List<Response.ResponseListener> listeners = exchange.getResponseListeners();
            HttpContentResponse contentResponse = new HttpContentResponse(response, getContent(), getEncoding());
            notifier.forwardFailureComplete(listeners, exchange.getRequest(), exchange.getRequestFailure(), contentResponse, failure);
        }

        @Override
        public void onComplete(Result result) {
        }
    }
}
