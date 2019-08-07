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

package Applications.jetty.websocket.common.extensions;

import Applications.jetty.io.ByteBufferPool;
import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WebSocketPolicy;
import Applications.jetty.websocket.api.WriteCallback;
import Applications.jetty.websocket.api.extensions.*;
import Applications.jetty.websocket.common.LogicalConnection;

import java.io.IOException;

@ManagedObject("Abstract Extension")
public abstract class AbstractExtension extends ContainerLifeCycle implements Extension {
    private final Logger log;
    private WebSocketPolicy policy;
    private ByteBufferPool bufferPool;
    private ExtensionConfig config;
    private LogicalConnection connection;
    private OutgoingFrames nextOutgoing;
    private IncomingFrames nextIncoming;

    public AbstractExtension() {
        log = Log.getLogger(this.getClass());
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        super.dump(out, indent);
        // incoming
        dumpWithHeading(out, indent, "incoming", this.nextIncoming);
        dumpWithHeading(out, indent, "outgoing", this.nextOutgoing);
    }

    protected void dumpWithHeading(Appendable out, String indent, String heading, Object bean) throws IOException {
        out.append(indent).append(" +- ");
        out.append(heading).append(" : ");
        out.append(bean.toString());
    }

    public ByteBufferPool getBufferPool() {
        return bufferPool;
    }

    @Override
    public ExtensionConfig getConfig() {
        return config;
    }

    public LogicalConnection getConnection() {
        return connection;
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @ManagedAttribute(name = "Next Incoming Frame Handler", readonly = true)
    public IncomingFrames getNextIncoming() {
        return nextIncoming;
    }

    @ManagedAttribute(name = "Next Outgoing Frame Handler", readonly = true)
    public OutgoingFrames getNextOutgoing() {
        return nextOutgoing;
    }

    public WebSocketPolicy getPolicy() {
        return policy;
    }

    @Override
    public void incomingError(Throwable e) {
        nextIncomingError(e);
    }

    /**
     * Used to indicate that the extension makes use of the RSV1 bit of the base websocket framing.
     * <p>
     * This is used to adjust validation during parsing, as well as a checkpoint against 2 or more extensions all simultaneously claiming ownership of RSV1.
     *
     * @return true if extension uses RSV1 for its own purposes.
     */
    @Override
    public boolean isRsv1User() {
        return false;
    }

    /**
     * Used to indicate that the extension makes use of the RSV2 bit of the base websocket framing.
     * <p>
     * This is used to adjust validation during parsing, as well as a checkpoint against 2 or more extensions all simultaneously claiming ownership of RSV2.
     *
     * @return true if extension uses RSV2 for its own purposes.
     */
    @Override
    public boolean isRsv2User() {
        return false;
    }

    /**
     * Used to indicate that the extension makes use of the RSV3 bit of the base websocket framing.
     * <p>
     * This is used to adjust validation during parsing, as well as a checkpoint against 2 or more extensions all simultaneously claiming ownership of RSV3.
     *
     * @return true if extension uses RSV3 for its own purposes.
     */
    @Override
    public boolean isRsv3User() {
        return false;
    }

    protected void nextIncomingError(Throwable e) {
        this.nextIncoming.incomingError(e);
    }

    protected void nextIncomingFrame(Frame frame) {
        log.debug("nextIncomingFrame({})", frame);
        this.nextIncoming.incomingFrame(frame);
    }

    protected void nextOutgoingFrame(Frame frame, WriteCallback callback) {
        log.debug("nextOutgoingFrame({})", frame);
        this.nextOutgoing.outgoingFrame(frame, callback);
    }

    public void setBufferPool(ByteBufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    public void setConfig(ExtensionConfig config) {
        this.config = config;
    }

    public void setConnection(LogicalConnection connection) {
        this.connection = connection;
    }

    @Override
    public void setNextIncomingFrames(IncomingFrames nextIncoming) {
        this.nextIncoming = nextIncoming;
    }

    @Override
    public void setNextOutgoingFrames(OutgoingFrames nextOutgoing) {
        this.nextOutgoing = nextOutgoing;
    }

    public void setPolicy(WebSocketPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", this.getClass().getSimpleName(), config.getParameterizedName());
    }
}
