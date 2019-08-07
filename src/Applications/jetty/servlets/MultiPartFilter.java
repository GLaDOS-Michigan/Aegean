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

import Applications.jetty.http.MimeTypes;
import Applications.jetty.util.IO;
import Applications.jetty.util.LazyList;
import Applications.jetty.util.MultiMap;
import Applications.jetty.util.MultiPartInputStreamParser;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;


/* ------------------------------------------------------------ */

/**
 * Multipart Form Data Filter.
 * <p>
 * This class decodes the multipart/form-data stream sent by a HTML form that uses a file input
 * item.  Any files sent are stored to a temporary file and a File object added to the request
 * as an attribute.  All other values are made available via the normal getParameter API and
 * the setCharacterEncoding mechanism is respected when converting bytes to Strings.
 * <p>
 * If the init parameter "delete" is set to "true", any files created will be deleted when the
 * current request returns.
 * <p>
 * The init parameter maxFormKeys sets the maximum number of keys that may be present in a
 * form (default set by system property org.eclipse.jetty.server.Request.maxFormKeys or 1000) to protect
 * against DOS attacks by bad hash keys.
 * <p>
 * The init parameter deleteFiles controls if uploaded files are automatically deleted after the request
 * completes.
 * <p>
 * Use init parameter "maxFileSize" to set the max size file that can be uploaded.
 * <p>
 * Use init parameter "maxRequestSize" to limit the size of the multipart request.
 */
public class MultiPartFilter implements Filter {
    private static final Logger LOG = Log.getLogger(MultiPartFilter.class);
    public final static String CONTENT_TYPE_SUFFIX = ".org.eclipse.jetty.servlet.contentType";
    private final static String MULTIPART = "org.eclipse.jetty.servlet.MultiPartFile.multiPartInputStream";
    private File tempdir;
    private boolean _deleteFiles;
    private ServletContext _context;
    private int _fileOutputBuffer = 0;
    private long _maxFileSize = -1L;
    private long _maxRequestSize = -1L;
    private int _maxFormKeys = Integer.getInteger("org.eclipse.jetty.server.Request.maxFormKeys", 1000);

    /* ------------------------------------------------------------------------------- */

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        tempdir = (File) filterConfig.getServletContext().getAttribute("javax.servlet.context.tempdir");
        _deleteFiles = "true".equals(filterConfig.getInitParameter("deleteFiles"));
        String fileOutputBuffer = filterConfig.getInitParameter("fileOutputBuffer");
        if (fileOutputBuffer != null)
            _fileOutputBuffer = Integer.parseInt(fileOutputBuffer);
        String maxFileSize = filterConfig.getInitParameter("maxFileSize");
        if (maxFileSize != null)
            _maxFileSize = Long.parseLong(maxFileSize.trim());
        String maxRequestSize = filterConfig.getInitParameter("maxRequestSize");
        if (maxRequestSize != null)
            _maxRequestSize = Long.parseLong(maxRequestSize.trim());

        _context = filterConfig.getServletContext();
        String mfks = filterConfig.getInitParameter("maxFormKeys");
        if (mfks != null)
            _maxFormKeys = Integer.parseInt(mfks);
    }

    /* ------------------------------------------------------------------------------- */

    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest srequest = (HttpServletRequest) request;
        if (srequest.getContentType() == null || !srequest.getContentType().startsWith("multipart/form-data")) {
            chain.doFilter(request, response);
            return;
        }

        InputStream in = new BufferedInputStream(request.getInputStream());
        String content_type = srequest.getContentType();

        //Get current parameters so we can merge into them
        MultiMap params = new MultiMap();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String[])
                params.addValues(entry.getKey(), (String[]) value);
            else
                params.add(entry.getKey(), value);
        }

        MultipartConfigElement config = new MultipartConfigElement(tempdir.getCanonicalPath(), _maxFileSize, _maxRequestSize, _fileOutputBuffer);
        MultiPartInputStreamParser mpis = new MultiPartInputStreamParser(in, content_type, config, tempdir);
        mpis.setDeleteOnExit(_deleteFiles);
        request.setAttribute(MULTIPART, mpis);
        try {
            Collection<Part> parts = mpis.getParts();
            if (parts != null) {
                Iterator<Part> itor = parts.iterator();
                while (itor.hasNext() && params.size() < _maxFormKeys) {
                    Part p = itor.next();
                    MultiPartInputStreamParser.MultiPart mp = (MultiPartInputStreamParser.MultiPart) p;
                    if (mp.getFile() != null) {
                        request.setAttribute(mp.getName(), mp.getFile());
                        if (mp.getContentDispositionFilename() != null) {
                            params.add(mp.getName(), mp.getContentDispositionFilename());
                            if (mp.getContentType() != null)
                                params.add(mp.getName() + CONTENT_TYPE_SUFFIX, mp.getContentType());
                        }
                    } else {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        IO.copy(p.getInputStream(), bytes);
                        params.add(p.getName(), bytes.toByteArray());
                        if (p.getContentType() != null)
                            params.add(p.getName() + CONTENT_TYPE_SUFFIX, p.getContentType());
                    }
                }
            }

            // handle request
            chain.doFilter(new Wrapper(srequest, params), response);
        } finally {
            deleteFiles(request);
        }
    }


    /* ------------------------------------------------------------ */
    private void deleteFiles(ServletRequest request) {
        if (!_deleteFiles)
            return;

        MultiPartInputStreamParser mpis = (MultiPartInputStreamParser) request.getAttribute(MULTIPART);
        if (mpis != null) {
            try {
                mpis.deleteParts();
            } catch (Exception e) {
                _context.log("Error deleting multipart tmp files", e);
            }
        }
        request.removeAttribute(MULTIPART);
    }

    /* ------------------------------------------------------------------------------- */

    /**
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
    }

    /* ------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------- */
    private static class Wrapper extends HttpServletRequestWrapper {
        Charset _encoding = StandardCharsets.UTF_8;
        MultiMap<Object> _params;

        /* ------------------------------------------------------------------------------- */

        /**
         * Constructor.
         *
         * @param request
         */
        public Wrapper(HttpServletRequest request, MultiMap map) {
            super(request);
            this._params = map;
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#getContentLength()
         */
        @Override
        public int getContentLength() {
            return 0;
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#getParameter(java.lang.String)
         */
        @Override
        public String getParameter(String name) {
            Object o = _params.get(name);
            if (!(o instanceof byte[]) && LazyList.size(o) > 0)
                o = LazyList.get(o, 0);

            if (o instanceof byte[]) {
                try {
                    return getParameterBytesAsString(name, (byte[]) o);
                } catch (Exception e) {
                    LOG.warn(e);
                }
            } else if (o != null)
                return String.valueOf(o);
            return null;
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#getParameterMap()
         */
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> cmap = new HashMap<String, String[]>();

            for (Object key : _params.keySet()) {
                cmap.put((String) key, getParameterValues((String) key));
            }

            return Collections.unmodifiableMap(cmap);
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#getParameterNames()
         */
        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(_params.keySet());
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#getParameterValues(java.lang.String)
         */
        @Override
        public String[] getParameterValues(String name) {
            List l = _params.getValues(name);
            if (l == null || l.size() == 0)
                return new String[0];
            String[] v = new String[l.size()];
            for (int i = 0; i < l.size(); i++) {
                Object o = l.get(i);
                if (o instanceof byte[]) {
                    try {
                        v[i] = getParameterBytesAsString(name, (byte[]) o);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (o instanceof String)
                    v[i] = (String) o;
            }
            return v;
        }

        /* ------------------------------------------------------------------------------- */

        /**
         * @see javax.servlet.ServletRequest#setCharacterEncoding(java.lang.String)
         */
        @Override
        public void setCharacterEncoding(String enc)
                throws UnsupportedEncodingException {
            try {
                _encoding = Charset.forName(enc);
            } catch (UnsupportedCharsetException e) {
                throw new UnsupportedEncodingException(e.getMessage());
            }
        }


        /* ------------------------------------------------------------------------------- */
        private String getParameterBytesAsString(String name, byte[] bytes)
                throws UnsupportedEncodingException {
            //check if there is a specific encoding for the parameter
            Object ct = _params.getValue(name + CONTENT_TYPE_SUFFIX, 0);
            //use default if not
            Charset contentType = _encoding;
            if (ct != null) {
                String tmp = MimeTypes.getCharsetFromContentType((String) ct);
                try {
                    contentType = (tmp == null ? _encoding : Charset.forName(tmp));
                } catch (UnsupportedCharsetException e) {
                    throw new UnsupportedEncodingException(e.getMessage());
                }
            }

            return new String(bytes, contentType);
        }
    }
}
