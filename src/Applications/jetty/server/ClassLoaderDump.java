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

package Applications.jetty.server;

import Applications.jetty.util.TypeUtil;
import Applications.jetty.util.component.ContainerLifeCycle;
import Applications.jetty.util.component.Dumpable;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Collections;

public class ClassLoaderDump implements Dumpable {
    final ClassLoader _loader;

    public ClassLoaderDump(ClassLoader loader) {
        _loader = loader;
    }

    @Override
    public String dump() {
        return ContainerLifeCycle.dump(this);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        if (_loader == null)
            out.append("No ClassLoader\n");
        else {
            out.append(String.valueOf(_loader)).append("\n");

            Object parent = _loader.getParent();
            if (parent != null) {
                if (!(parent instanceof Dumpable))
                    parent = new ClassLoaderDump((ClassLoader) parent);

                if (_loader instanceof URLClassLoader)
                    ContainerLifeCycle.dump(out, indent, TypeUtil.asList(((URLClassLoader) _loader).getURLs()), Collections.singleton(parent));
                else
                    ContainerLifeCycle.dump(out, indent, Collections.singleton(parent));
            }
        }
    }
}
