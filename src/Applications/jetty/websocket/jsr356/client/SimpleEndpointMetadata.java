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

package Applications.jetty.websocket.jsr356.client;

import Applications.jetty.websocket.jsr356.metadata.DecoderMetadataSet;
import Applications.jetty.websocket.jsr356.metadata.EncoderMetadataSet;
import Applications.jetty.websocket.jsr356.metadata.EndpointMetadata;

import javax.websocket.Endpoint;

/**
 * Basic {@link EndpointMetadata} for an WebSocket that extends from {@link Endpoint}
 */
public class SimpleEndpointMetadata implements EndpointMetadata {
    private final Class<?> endpointClass;
    private DecoderMetadataSet decoders;
    private EncoderMetadataSet encoders;

    public SimpleEndpointMetadata(Class<? extends Endpoint> endpointClass) {
        this.endpointClass = endpointClass;
        this.decoders = new DecoderMetadataSet();
        this.encoders = new EncoderMetadataSet();
    }

    @Override
    public DecoderMetadataSet getDecoders() {
        return decoders;
    }

    @Override
    public EncoderMetadataSet getEncoders() {
        return encoders;
    }

    @Override
    public Class<?> getEndpointClass() {
        return endpointClass;
    }
}
