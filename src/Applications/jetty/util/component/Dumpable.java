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

package Applications.jetty.util.component;

import Applications.jetty.util.annotation.ManagedObject;
import Applications.jetty.util.annotation.ManagedOperation;

import java.io.IOException;

@ManagedObject("Dumpable Object")
public interface Dumpable {
    @ManagedOperation(value = "Dump the nested Object state as a String", impact = "INFO")
    String dump();

    void dump(Appendable out, String indent) throws IOException;
}
