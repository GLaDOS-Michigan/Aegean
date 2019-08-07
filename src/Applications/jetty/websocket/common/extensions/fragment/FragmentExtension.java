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

package Applications.jetty.websocket.common.extensions.fragment;


import Applications.jetty.websocket.api.WriteCallback;
import Applications.jetty.websocket.api.extensions.ExtensionConfig;
import Applications.jetty.websocket.api.extensions.Frame;
import Applications.jetty.websocket.common.OpCode;
import Applications.jetty.websocket.common.extensions.AbstractExtension;
import Applications.jetty.websocket.common.frames.DataFrame;

import java.nio.ByteBuffer;

/**
 * Fragment Extension
 */
public class FragmentExtension extends AbstractExtension {
    private int maxLength = -1;

    @Override
    public String getName() {
        return "fragment";
    }

    @Override
    public void incomingError(Throwable e) {
        // Pass thru
        nextIncomingError(e);
    }

    @Override
    public void incomingFrame(Frame frame) {
        // Pass thru
        nextIncomingFrame(frame);
    }

    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback) {
        if (OpCode.isControlFrame(frame.getOpCode())) {
            // Cannot fragment Control Frames
            nextOutgoingFrame(frame, callback);
            return;
        }

        int length = frame.getPayloadLength();

        ByteBuffer payload = frame.getPayload().slice();
        int originalLimit = payload.limit();
        int currentPosition = payload.position();

        if (maxLength <= 0) {
            // output original frame
            nextOutgoingFrame(frame, callback);
            return;
        }

        boolean continuation = false;

        // break apart payload based on maxLength rules
        while (length > maxLength) {
            DataFrame frag = new DataFrame(frame, continuation);
            frag.setFin(false); // always false here
            payload.position(currentPosition);
            payload.limit(Math.min(payload.position() + maxLength, originalLimit));
            frag.setPayload(payload);

            // no callback for beginning and middle parts
            nextOutgoingFrame(frag, null);

            length -= maxLength;
            continuation = true;
            currentPosition = payload.limit();
        }

        // write remaining
        DataFrame frag = new DataFrame(frame, continuation);
        frag.setFin(frame.isFin()); // use original fin
        payload.position(currentPosition);
        payload.limit(originalLimit);
        frag.setPayload(payload);

        nextOutgoingFrame(frag, callback);
    }

    @Override
    public void setConfig(ExtensionConfig config) {
        super.setConfig(config);

        maxLength = config.getParameter("maxLength", maxLength);
    }
}
