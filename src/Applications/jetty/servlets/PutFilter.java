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

package Applications.jetty.servlets;

import Applications.jetty.util.IO;
import Applications.jetty.util.URIUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PutFilter
 * <p>
 * A Filter that handles PUT, DELETE and MOVE methods.
 * Files are hidden during PUT operations, so that 404's result.
 * <p>
 * The following init parameters pay be used:<ul>
 * <li><b>baseURI</b> - The file URI of the document root for put content.
 * <li><b>delAllowed</b> - boolean, if true DELETE and MOVE methods are supported.
 * <li><b>putAtomic</b> - boolean, if true PUT files are written to a temp location and moved into place.
 * </ul>
 */
public class PutFilter implements Filter {
    public final static String __PUT = "PUT";
    public final static String __DELETE = "DELETE";
    public final static String __MOVE = "MOVE";
    public final static String __OPTIONS = "OPTIONS";

    Set<String> _operations = new HashSet<String>();
    private ConcurrentMap<String, String> _hidden = new ConcurrentHashMap<String, String>();

    private ServletContext _context;
    private String _baseURI;
    private boolean _delAllowed;
    private boolean _putAtomic;
    private File _tmpdir;


    /* ------------------------------------------------------------ */
    public void init(FilterConfig config) throws ServletException {
        _context = config.getServletContext();

        _tmpdir = (File) _context.getAttribute("javax.servlet.context.tempdir");

        if (_context.getRealPath("/") == null)
            throw new UnavailableException("Packed war");

        String b = config.getInitParameter("baseURI");
        if (b != null) {
            _baseURI = b;
        } else {
            File base = new File(_context.getRealPath("/"));
            _baseURI = base.toURI().toString();
        }

        _delAllowed = getInitBoolean(config, "delAllowed");
        _putAtomic = getInitBoolean(config, "putAtomic");

        _operations.add(__OPTIONS);
        _operations.add(__PUT);
        if (_delAllowed) {
            _operations.add(__DELETE);
            _operations.add(__MOVE);
        }
    }

    /* ------------------------------------------------------------ */
    private boolean getInitBoolean(FilterConfig config, String name) {
        String value = config.getInitParameter(name);
        return value != null && value.length() > 0 && (value.startsWith("t") || value.startsWith("T") || value.startsWith("y") || value.startsWith("Y") || value.startsWith("1"));
    }

    /* ------------------------------------------------------------ */
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String pathInContext = URIUtil.addPaths(servletPath, pathInfo);

        String resource = URIUtil.addPaths(_baseURI, pathInContext);

        String method = request.getMethod();
        boolean op = _operations.contains(method);

        if (op) {
            File file = null;
            try {
                if (method.equals(__OPTIONS))
                    handleOptions(chain, request, response);
                else {
                    file = new File(new URI(resource));
                    boolean exists = file.exists();
                    if (exists && !passConditionalHeaders(request, response, file))
                        return;

                    if (method.equals(__PUT))
                        handlePut(request, response, pathInContext, file);
                    else if (method.equals(__DELETE))
                        handleDelete(request, response, pathInContext, file);
                    else if (method.equals(__MOVE))
                        handleMove(request, response, pathInContext, file);
                    else
                        throw new IllegalStateException();
                }
            } catch (Exception e) {
                _context.log(e.toString(), e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            if (isHidden(pathInContext))
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            else
                chain.doFilter(request, response);
            return;
        }
    }

    /* ------------------------------------------------------------ */
    private boolean isHidden(String pathInContext) {
        return _hidden.containsKey(pathInContext);
    }

    /* ------------------------------------------------------------ */
    public void destroy() {
    }

    /* ------------------------------------------------------------------- */
    public void handlePut(HttpServletRequest request, HttpServletResponse response, String pathInContext, File file) throws ServletException, IOException {
        boolean exists = file.exists();
        if (pathInContext.endsWith("/")) {
            if (!exists) {
                if (!file.mkdirs())
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                else {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    response.flushBuffer();
                }
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.flushBuffer();
            }
        } else {
            boolean ok = false;
            try {
                _hidden.put(pathInContext, pathInContext);
                File parent = file.getParentFile();
                parent.mkdirs();
                int toRead = request.getContentLength();
                InputStream in = request.getInputStream();


                if (_putAtomic) {
                    File tmp = File.createTempFile(file.getName(), null, _tmpdir);
                    try (OutputStream out = new FileOutputStream(tmp, false)) {
                        if (toRead >= 0)
                            IO.copy(in, out, toRead);
                        else
                            IO.copy(in, out);
                    }

                    if (!tmp.renameTo(file))
                        throw new IOException("rename from " + tmp + " to " + file + " failed");
                } else {
                    try (OutputStream out = new FileOutputStream(file, false)) {
                        if (toRead >= 0)
                            IO.copy(in, out, toRead);
                        else
                            IO.copy(in, out);
                    }
                }

                response.setStatus(exists ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
                response.flushBuffer();
                ok = true;
            } catch (Exception ex) {
                _context.log(ex.toString(), ex);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } finally {
                if (!ok) {
                    try {
                        if (file.exists())
                            file.delete();
                    } catch (Exception e) {
                        _context.log(e.toString(), e);
                    }
                }
                _hidden.remove(pathInContext);
            }
        }
    }

    /* ------------------------------------------------------------------- */
    public void handleDelete(HttpServletRequest request, HttpServletResponse response, String pathInContext, File file) throws ServletException, IOException {
        try {
            // delete the file
            if (file.delete()) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                response.flushBuffer();
            } else
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (SecurityException sex) {
            _context.log(sex.toString(), sex);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    /* ------------------------------------------------------------------- */
    public void handleMove(HttpServletRequest request, HttpServletResponse response, String pathInContext, File file)
            throws ServletException, IOException, URISyntaxException {
        String newPath = URIUtil.canonicalPath(request.getHeader("new-uri"));
        if (newPath == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String contextPath = request.getContextPath();
        if (contextPath != null && !newPath.startsWith(contextPath)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        String newInfo = newPath;
        if (contextPath != null)
            newInfo = newInfo.substring(contextPath.length());

        String new_resource = URIUtil.addPaths(_baseURI, newInfo);
        File new_file = new File(new URI(new_resource));

        file.renameTo(new_file);

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.flushBuffer();
    }

    /* ------------------------------------------------------------ */
    public void handleOptions(FilterChain chain, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                if ("Allow".equalsIgnoreCase(name)) {
                    Set<String> options = new HashSet<String>();
                    options.addAll(Arrays.asList(value.split(" *, *")));
                    options.addAll(_operations);
                    value = null;
                    for (String o : options)
                        value = value == null ? o : (value + ", " + o);
                }

                super.setHeader(name, value);
            }
        });

    }

    /* ------------------------------------------------------------ */
    /*
     * Check modification date headers.
     */
    protected boolean passConditionalHeaders(HttpServletRequest request, HttpServletResponse response, File file) throws IOException {
        long date = 0;

        if ((date = request.getDateHeader("if-unmodified-since")) > 0) {
            if (file.lastModified() / 1000 > date / 1000) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }

        if ((date = request.getDateHeader("if-modified-since")) > 0) {
            if (file.lastModified() / 1000 <= date / 1000) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                response.flushBuffer();
                return false;
            }
        }
        return true;
    }
}
