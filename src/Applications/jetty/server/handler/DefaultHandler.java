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

package Applications.jetty.server.handler;

import Applications.jetty.http.HttpHeader;
import Applications.jetty.http.HttpMethod;
import Applications.jetty.http.MimeTypes;
import Applications.jetty.server.Handler;
import Applications.jetty.server.Request;
import Applications.jetty.server.Server;
import Applications.jetty.util.ByteArrayISO8859Writer;
import Applications.jetty.util.IO;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;
import Applications.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;


/* ------------------------------------------------------------ */

/**
 * Default Handler.
 * <p>
 * This handle will deal with unhandled requests in the server.
 * For requests for favicon.ico, the Jetty icon is served.
 * For reqests to '/' a 404 with a list of known contexts is served.
 * For all other requests a normal 404 is served.
 *
 * @org.apache.xbean.XBean
 */
public class DefaultHandler extends AbstractHandler {
    private static final Logger LOG = Log.getLogger(DefaultHandler.class);

    final long _faviconModified = (System.currentTimeMillis() / 1000) * 1000L;
    byte[] _favicon;
    boolean _serveIcon = true;
    boolean _showContexts = true;

    public DefaultHandler() {
        try {
            URL fav = this.getClass().getClassLoader().getResource("org/eclipse/jetty/favicon.ico");
            if (fav != null) {
                Resource r = Resource.newResource(fav);
                _favicon = IO.readBytes(r.getInputStream());
            }
        } catch (Exception e) {
            LOG.warn(e);
        }
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.jetty.server.server.Handler#handle(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, int)
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (response.isCommitted() || baseRequest.isHandled())
            return;

        baseRequest.setHandled(true);

        String method = request.getMethod();

        // little cheat for common request
        if (_serveIcon && _favicon != null && HttpMethod.GET.is(method) && request.getRequestURI().equals("/favicon.ico")) {
            if (request.getDateHeader(HttpHeader.IF_MODIFIED_SINCE.toString()) == _faviconModified)
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("image/x-icon");
                response.setContentLength(_favicon.length);
                response.setDateHeader(HttpHeader.LAST_MODIFIED.toString(), _faviconModified);
                response.setHeader(HttpHeader.CACHE_CONTROL.toString(), "max-age=360000,public");
                response.getOutputStream().write(_favicon);
            }
            return;
        }


        if (!_showContexts || !HttpMethod.GET.is(method) || !request.getRequestURI().equals("/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MimeTypes.Type.TEXT_HTML.toString());

        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);

        writer.write("<HTML>\n<HEAD>\n<TITLE>Error 404 - Not Found");
        writer.write("</TITLE>\n<BODY>\n<H2>Error 404 - Not Found.</H2>\n");
        writer.write("No context on this server matched or handled this request.<BR>");
        writer.write("Contexts known to this server are: <ul>");

        Server server = getServer();
        Handler[] handlers = server == null ? null : server.getChildHandlersByClass(ContextHandler.class);

        for (int i = 0; handlers != null && i < handlers.length; i++) {
            ContextHandler context = (ContextHandler) handlers[i];
            if (context.isRunning()) {
                writer.write("<li><a href=\"");
                if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0)
                    writer.write("http://" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                writer.write(context.getContextPath());
                if (context.getContextPath().length() > 1 && context.getContextPath().endsWith("/"))
                    writer.write("/");
                writer.write("\">");
                writer.write(context.getContextPath());
                if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0)
                    writer.write("&nbsp;@&nbsp;" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                writer.write("&nbsp;--->&nbsp;");
                writer.write(context.toString());
                writer.write("</a></li>\n");
            } else {
                writer.write("<li>");
                writer.write(context.getContextPath());
                if (context.getVirtualHosts() != null && context.getVirtualHosts().length > 0)
                    writer.write("&nbsp;@&nbsp;" + context.getVirtualHosts()[0] + ":" + request.getLocalPort());
                writer.write("&nbsp;--->&nbsp;");
                writer.write(context.toString());
                if (context.isFailed())
                    writer.write(" [failed]");
                if (context.isStopped())
                    writer.write(" [stopped]");
                writer.write("</li>\n");
            }
        }

        writer.write("</ul><hr>");
        writer.write("<a href=\"http://eclipse.org/jetty\"><img border=0 src=\"/favicon.ico\"/></a>&nbsp;");
        writer.write("<a href=\"http://eclipse.org/jetty\">Powered by Jetty:// Java Web Server</a><hr/>\n");

        writer.write("\n</BODY>\n</HTML>\n");
        writer.flush();
        response.setContentLength(writer.size());
        try (OutputStream out = response.getOutputStream()) {
            writer.writeTo(out);
        }
    }

    /* ------------------------------------------------------------ */

    /**
     * @return Returns true if the handle can server the jetty favicon.ico
     */
    public boolean getServeIcon() {
        return _serveIcon;
    }

    /* ------------------------------------------------------------ */

    /**
     * @param serveIcon true if the handle can server the jetty favicon.ico
     */
    public void setServeIcon(boolean serveIcon) {
        _serveIcon = serveIcon;
    }

    public boolean getShowContexts() {
        return _showContexts;
    }

    public void setShowContexts(boolean show) {
        _showContexts = show;
    }

}
