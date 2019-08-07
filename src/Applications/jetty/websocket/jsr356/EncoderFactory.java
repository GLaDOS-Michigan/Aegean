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

package Applications.jetty.websocket.jsr356;

import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.jsr356.metadata.EncoderMetadata;
import Applications.jetty.websocket.jsr356.metadata.EncoderMetadataSet;

import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents all of the declared {@link Encoder}s that the Container is aware of.
 */
public class EncoderFactory implements Configurable {
    public static class Wrapper implements Configurable {
        private final Encoder encoder;
        private final EncoderMetadata metadata;

        private Wrapper(Encoder encoder, EncoderMetadata metadata) {
            this.encoder = encoder;
            this.metadata = metadata;
        }

        public Encoder getEncoder() {
            return encoder;
        }

        public EncoderMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void init(EndpointConfig config) {
            this.encoder.init(config);
        }
    }

    private static final Logger LOG = Log.getLogger(EncoderFactory.class);

    private final EncoderMetadataSet metadatas;
    private EncoderFactory parentFactory;
    private Map<Class<?>, Wrapper> activeWrappers;

    public EncoderFactory(EncoderMetadataSet metadatas) {
        this.metadatas = metadatas;
        this.activeWrappers = new ConcurrentHashMap<>();
    }

    public EncoderFactory(EncoderMetadataSet metadatas, EncoderFactory parentFactory) {
        this(metadatas);
        this.parentFactory = parentFactory;
    }

    public Encoder getEncoderFor(Class<?> type) {
        Wrapper wrapper = getWrapperFor(type);
        if (wrapper == null) {
            return null;
        }
        return wrapper.encoder;
    }

    public EncoderMetadata getMetadataFor(Class<?> type) {
        LOG.debug("getMetadataFor({})", type);
        EncoderMetadata metadata = metadatas.getMetadataByType(type);

        if (metadata != null) {
            return metadata;
        }

        if (parentFactory != null) {
            return parentFactory.getMetadataFor(type);
        }

        return null;
    }

    public Wrapper getWrapperFor(Class<?> type) {
        synchronized (activeWrappers) {
            Wrapper wrapper = activeWrappers.get(type);

            // Try parent (if needed)
            if ((wrapper == null) && (parentFactory != null)) {
                wrapper = parentFactory.getWrapperFor(type);
            }

            if (wrapper == null) {
                // Attempt to create Wrapper on demand
                EncoderMetadata metadata = metadatas.getMetadataByType(type);
                if (metadata == null) {
                    return null;
                }
                wrapper = newWrapper(metadata);
                // track wrapper
                activeWrappers.put(type, wrapper);
            }

            return wrapper;
        }
    }

    @Override
    public void init(EndpointConfig config) {
        LOG.debug("init({})", config);

        // Instantiate all declared encoders
        for (EncoderMetadata metadata : metadatas) {
            Wrapper wrapper = newWrapper(metadata);
            activeWrappers.put(metadata.getObjectType(), wrapper);
        }

        // Initialize all encoders
        for (Wrapper wrapper : activeWrappers.values()) {
            wrapper.encoder.init(config);
        }
    }

    private Wrapper newWrapper(EncoderMetadata metadata) {
        Class<? extends Encoder> encoderClass = metadata.getCoderClass();
        try {
            Encoder encoder = encoderClass.newInstance();
            return new Wrapper(encoder, metadata);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to instantiate Encoder: " + encoderClass.getName());
        }
    }
}
