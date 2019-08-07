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

package Applications.jetty.webapp;

import Applications.jetty.xml.XmlParser;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * IterativeDescriptorProcessor
 */
public abstract class IterativeDescriptorProcessor implements DescriptorProcessor {
    public static final Class<?>[] __signature = new Class[]{WebAppContext.class, Descriptor.class, XmlParser.Node.class};
    protected Map<String, Method> _visitors = new HashMap<String, Method>();

    public abstract void start(WebAppContext context, Descriptor descriptor);

    public abstract void end(WebAppContext context, Descriptor descriptor);

    /**
     * Register a method to be called back when visiting the node with the given name.
     * The method must exist on a subclass of this class, and must have the signature:
     * public void method (Descriptor descriptor, XmlParser.Node node)
     *
     * @param nodeName
     * @param m
     */
    public void registerVisitor(String nodeName, Method m) {
        _visitors.put(nodeName, m);
    }


    /**
     * {@inheritDoc}
     */
    public void process(WebAppContext context, Descriptor descriptor)
            throws Exception {
        if (descriptor == null)
            return;

        start(context, descriptor);

        XmlParser.Node root = descriptor.getRoot();
        Iterator<?> iter = root.iterator();
        XmlParser.Node node = null;
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof XmlParser.Node)) continue;
            node = (XmlParser.Node) o;
            visit(context, descriptor, node);
        }

        end(context, descriptor);
    }


    protected void visit(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
            throws Exception {
        String name = node.getTag();
        Method m = _visitors.get(name);
        if (m != null)
            m.invoke(this, new Object[]{context, descriptor, node});
    }
}
