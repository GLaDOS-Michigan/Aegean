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

package Applications.jetty.websocket.common.frames;

import Applications.jetty.websocket.api.ProtocolException;
import Applications.jetty.websocket.common.WebSocketFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class ControlFrame extends WebSocketFrame {
    /**
     * Maximum size of Control frame, per RFC 6455
     */
    public static final int MAX_CONTROL_PAYLOAD = 125;

    public ControlFrame(byte opcode) {
        super(opcode);
    }

    public void assertValid() {
        if (isControlFrame()) {
            if (getPayloadLength() > ControlFrame.MAX_CONTROL_PAYLOAD) {
                throw new ProtocolException("Desired payload length [" + getPayloadLength() + "] exceeds maximum control payload length ["
                        + MAX_CONTROL_PAYLOAD + "]");
            }

            if ((finRsvOp & 0x80) == 0) {
                throw new ProtocolException("Cannot have FIN==false on Control frames");
            }

            if ((finRsvOp & 0x40) != 0) {
                throw new ProtocolException("Cannot have RSV1==true on Control frames");
            }

            if ((finRsvOp & 0x20) != 0) {
                throw new ProtocolException("Cannot have RSV2==true on Control frames");
            }

            if ((finRsvOp & 0x10) != 0) {
                throw new ProtocolException("Cannot have RSV3==true on Control frames");
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ControlFrame other = (ControlFrame) obj;
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        if (finRsvOp != other.finRsvOp) {
            return false;
        }
        if (!Arrays.equals(mask, other.mask)) {
            return false;
        }
        if (masked != other.masked) {
            return false;
        }
        return true;
    }

    public boolean isControlFrame() {
        return true;
    }

    @Override
    public boolean isDataFrame() {
        return false;
    }

    @Override
    public WebSocketFrame setPayload(ByteBuffer buf) {
        if (buf == null) {
            data = null;
            return this;
        }

        if (buf.remaining() > ControlFrame.MAX_CONTROL_PAYLOAD) {
            throw new ProtocolException("Control Payloads can not exceed 125 bytes in length.");
        }
        return super.setPayload(buf);
    }
}
