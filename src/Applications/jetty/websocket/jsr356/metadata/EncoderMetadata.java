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

package Applications.jetty.websocket.jsr356.metadata;

import Applications.jetty.websocket.jsr356.MessageType;

import javax.websocket.Encoder;

/**
 * Immutable Metadata for a {@link Encoder}
 */
public class EncoderMetadata extends CoderMetadata<Encoder> {
    public EncoderMetadata(Class<? extends Encoder> coderClass, Class<?> objType, MessageType messageType, boolean streamed) {
        super(coderClass, objType, messageType, streamed);
    }
}