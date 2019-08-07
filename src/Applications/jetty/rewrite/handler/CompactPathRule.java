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

package Applications.jetty.rewrite.handler;

import Applications.jetty.server.Request;
import Applications.jetty.util.URIUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Rewrite the URI by compacting to remove //
 */
public class CompactPathRule extends Rule implements Rule.ApplyURI {
    public CompactPathRule() {
        _handling = false;
        _terminating = false;
    }

    @Override
    public void applyURI(Request request, String oldTarget, String newTarget) throws IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/"))
            uri = URIUtil.compactPath(uri);
        request.setRequestURI(uri);
    }

    @Override
    public String matchAndApply(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (target.startsWith("/"))
            return URIUtil.compactPath(target);
        return target;
    }
}
