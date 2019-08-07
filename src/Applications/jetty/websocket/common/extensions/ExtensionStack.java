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

import Applications.jetty.util.annotation.ManagedAttribute;
import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.websocket.api.WriteCallback;
import Applications.jetty.websocket.api.extensions.*;
import Applications.jetty.websocket.common.Generator;
import Applications.jetty.websocket.common.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Represents the stack of Extensions.
 */
@ManagedObject("Extension Stack")
public class ExtensionStack extends ContainerLifeCycle implements IncomingFrames, OutgoingFrames {
    private static final Logger LOG = Log.getLogger(ExtensionStack.class);
    private final ExtensionFactory factory;
    private List<Extension> extensions;
    private IncomingFrames nextIncoming;
    private OutgoingFrames nextOutgoing;

    public ExtensionStack(ExtensionFactory factory) {
        this.factory = factory;
    }

    public void configure(Generator generator) {
        generator.configureFromExtensions(extensions);
    }

    public void configure(Parser parser) {
        parser.configureFromExtensions(extensions);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("doStart");

        // Wire up Extensions
        if ((extensions != null) && (extensions.size() > 0)) {
            ListIterator<Extension> eiter = extensions.listIterator();

            // Connect outgoings
            while (eiter.hasNext()) {
                Extension ext = eiter.next();
                ext.setNextOutgoingFrames(nextOutgoing);
                nextOutgoing = ext;
            }

            // Connect incomings
            while (eiter.hasPrevious()) {
                Extension ext = eiter.previous();
                ext.setNextIncomingFrames(nextIncoming);
                nextIncoming = ext;
            }
        }
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        super.dump(out, indent);

        IncomingFrames websocket = getLastIncoming();
        OutgoingFrames network = getLastOutgoing();

        out.append(indent).append(" +- Stack\n");
        out.append(indent).append("    +- Network  : ").append(network.toString()).append('\n');
        for (Extension ext : extensions) {
            out.append(indent).append("    +- Extension: ").append(ext.toString()).append('\n');
        }
        out.append(indent).append("    +- Websocket: ").append(websocket.toString()).append('\n');
    }

    @ManagedAttribute(name = "Extension List", readonly = true)
    public List<Extension> getExtensions() {
        return extensions;
    }

    private IncomingFrames getLastIncoming() {
        IncomingFrames last = nextIncoming;
        boolean done = false;
        while (!done) {
            if (last instanceof AbstractExtension) {
                last = ((AbstractExtension) last).getNextIncoming();
            } else {
                done = true;
            }
        }
        return last;
    }

    private OutgoingFrames getLastOutgoing() {
        OutgoingFrames last = nextOutgoing;
        boolean done = false;
        while (!done) {
            if (last instanceof AbstractExtension) {
                last = ((AbstractExtension) last).getNextOutgoing();
            } else {
                done = true;
            }
        }
        return last;
    }

    /**
     * Get the list of negotiated extensions, each entry being a full "name; params" extension configuration
     *
     * @return list of negotiated extensions
     */
    public List<ExtensionConfig> getNegotiatedExtensions() {
        List<ExtensionConfig> ret = new ArrayList<>();
        if (extensions == null) {
            return ret;
        }

        for (Extension ext : extensions) {
            ret.add(ext.getConfig());
        }
        return ret;
    }

    @ManagedAttribute(name = "Next Incoming Frames Handler", readonly = true)
    public IncomingFrames getNextIncoming() {
        return nextIncoming;
    }

    @ManagedAttribute(name = "Next Outgoing Frames Handler", readonly = true)
    public OutgoingFrames getNextOutgoing() {
        return nextOutgoing;
    }

    public boolean hasNegotiatedExtensions() {
        return (this.extensions != null) && (this.extensions.size() > 0);
    }

    @Override
    public void incomingError(Throwable e) {
        nextIncoming.incomingError(e);
    }

    @Override
    public void incomingFrame(Frame frame) {
        nextIncoming.incomingFrame(frame);
    }

    /**
     * Perform the extension negotiation.
     * <p>
     * For the list of negotiated extensions, use {@link #getNegotiatedExtensions()}
     *
     * @param configs the configurations being requested
     */
    public void negotiate(List<ExtensionConfig> configs) {
        LOG.debug("Extension Configs={}", configs);
        this.extensions = new ArrayList<>();
        for (ExtensionConfig config : configs) {
            Extension ext = factory.newInstance(config);
            if (ext == null) {
                // Extension not present on this side
                continue;
            }
            extensions.add(ext);
            LOG.debug("Adding Extension: {}", ext);
        }

        addBean(extensions);
    }

    @Override
    public void outgoingFrame(Frame frame, WriteCallback callback) {
        nextOutgoing.outgoingFrame(frame, callback);
    }

    public void setNextIncoming(IncomingFrames nextIncoming) {
        this.nextIncoming = nextIncoming;
    }

    public void setNextOutgoing(OutgoingFrames nextOutgoing) {
        this.nextOutgoing = nextOutgoing;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("ExtensionStack[");
        s.append("extensions=");
        if (extensions == null) {
            s.append("<null>");
        } else {
            s.append('[');
            boolean delim = false;
            for (Extension ext : extensions) {
                if (delim) {
                    s.append(',');
                }
                if (ext == null) {
                    s.append("<null>");
                } else {
                    s.append(ext.getName());
                }
                delim = true;
            }
            s.append(']');
        }
        s.append(",incoming=").append((this.nextIncoming == null) ? "<null>" : this.nextIncoming.getClass().getName());
        s.append(",outgoing=").append((this.nextOutgoing == null) ? "<null>" : this.nextOutgoing.getClass().getName());
        s.append("]");
        return s.toString();
    }
}

