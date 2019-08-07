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

package Applications.jetty.websocket.server.pathmap;

import Applications.jetty.util.URIUtil;

public class ServletPathSpec extends PathSpec {
    public static final String PATH_SPEC_SEPARATORS = ":,";

    /**
     * Get multi-path spec splits.
     *
     * @param servletPathSpec the path spec that might contain multiple declared path specs
     * @return the individual path specs found.
     */
    public static ServletPathSpec[] getMultiPathSpecs(String servletPathSpec) {
        String pathSpecs[] = servletPathSpec.split(PATH_SPEC_SEPARATORS);
        int len = pathSpecs.length;
        ServletPathSpec sps[] = new ServletPathSpec[len];
        for (int i = 0; i < len; i++) {
            sps[i] = new ServletPathSpec(pathSpecs[i]);
        }
        return sps;
    }

    public ServletPathSpec(String servletPathSpec) {
        super();
        assertValidServletPathSpec(servletPathSpec);

        // The Path Spec for Default Servlet
        if ((servletPathSpec == null) || (servletPathSpec.length() == 0) || "/".equals(servletPathSpec)) {
            super.pathSpec = "/";
            super.pathDepth = -1; // force this to be last in sort order
            this.specLength = 1;
            this.group = PathSpecGroup.DEFAULT;
            return;
        }

        this.specLength = servletPathSpec.length();
        super.pathDepth = 0;
        char lastChar = servletPathSpec.charAt(specLength - 1);
        // prefix based
        if ((servletPathSpec.charAt(0) == '/') && (specLength > 1) && (lastChar == '*')) {
            this.group = PathSpecGroup.PREFIX_GLOB;
        }
        // suffix based
        else if (servletPathSpec.charAt(0) == '*') {
            this.group = PathSpecGroup.SUFFIX_GLOB;
        } else {
            this.group = PathSpecGroup.EXACT;
        }

        for (int i = 0; i < specLength; i++) {
            int cp = servletPathSpec.codePointAt(i);
            if (cp < 128) {
                char c = (char) cp;
                switch (c) {
                    case '/':
                        super.pathDepth++;
                        break;
                }
            }
        }

        super.pathSpec = servletPathSpec;
    }

    private void assertValidServletPathSpec(String servletPathSpec) {
        if ((servletPathSpec == null) || servletPathSpec.equals("")) {
            return; // empty path spec
        }

        // Ensure we don't have path spec separators here in our single path spec.
        for (char c : PATH_SPEC_SEPARATORS.toCharArray()) {
            if (servletPathSpec.indexOf(c) >= 0) {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: encountered Path Spec Separator [" + PATH_SPEC_SEPARATORS
                        + "] within specified path spec. did you forget to split this path spec up?");
            }
        }

        int len = servletPathSpec.length();
        // path spec must either start with '/' or '*.'
        if (servletPathSpec.charAt(0) == '/') {
            // Prefix Based
            if (len == 1) {
                return; // simple '/' path spec
            }
            int idx = servletPathSpec.indexOf('*');
            if (idx < 0) {
                return; // no hit on glob '*'
            }
            // only allowed to have '*' at the end of the path spec
            if (idx != (len - 1)) {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: glob '*' can only exist at end of prefix based matches");
            }
        } else if (servletPathSpec.startsWith("*.")) {
            // Suffix Based
            int idx = servletPathSpec.indexOf('/');
            // cannot have path separator
            if (idx >= 0) {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: suffix based path spec cannot have path separators");
            }

            idx = servletPathSpec.indexOf('*', 2);
            // only allowed to have 1 glob '*', at the start of the path spec
            if (idx >= 1) {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: suffix based path spec cannot have multiple glob '*'");
            }
        } else {
            throw new IllegalArgumentException("Servlet Spec 12.2 violation: path spec must start with \"/\" or \"*.\"");
        }
    }

    @Override
    public String getPathInfo(String path) {
        // Path Info only valid for PREFIX_GLOB types
        if (group == PathSpecGroup.PREFIX_GLOB) {
            if (path.length() == (specLength - 2)) {
                return null;
            }
            return path.substring(specLength - 2);
        }

        return null;
    }

    @Override
    public String getPathMatch(String path) {
        switch (group) {
            case EXACT:
                if (pathSpec.equals(path)) {
                    return path;
                } else {
                    return null;
                }
            case PREFIX_GLOB:
                if (isWildcardMatch(path)) {
                    return path.substring(0, specLength - 2);
                } else {
                    return null;
                }
            case SUFFIX_GLOB:
                if (path.regionMatches(path.length() - (specLength - 1), pathSpec, 1, specLength - 1)) {
                    return path;
                } else {
                    return null;
                }
            case DEFAULT:
                return path;
            default:
                return null;
        }
    }

    @Override
    public String getRelativePath(String base, String path) {
        String info = getPathInfo(path);
        if (info == null) {
            info = path;
        }

        if (info.startsWith("./")) {
            info = info.substring(2);
        }
        if (base.endsWith(URIUtil.SLASH)) {
            if (info.startsWith(URIUtil.SLASH)) {
                path = base + info.substring(1);
            } else {
                path = base + info;
            }
        } else if (info.startsWith(URIUtil.SLASH)) {
            path = base + info;
        } else {
            path = base + URIUtil.SLASH + info;
        }
        return path;
    }

    private boolean isExactMatch(String path) {
        if (group == PathSpecGroup.EXACT) {
            if (pathSpec.equals(path)) {
                return true;
            }
            return (path.charAt(path.length() - 1) == '/') && (path.equals(pathSpec + '/'));
        }
        return false;
    }

    private boolean isWildcardMatch(String path) {
        // For a spec of "/foo/*" match "/foo" , "/foo/..." but not "/foobar"
        int cpl = specLength - 2;
        if ((group == PathSpecGroup.PREFIX_GLOB) && (path.regionMatches(0, pathSpec, 0, cpl))) {
            if ((path.length() == cpl) || ('/' == path.charAt(cpl))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(String path) {
        switch (group) {
            case EXACT:
                return isExactMatch(path);
            case PREFIX_GLOB:
                return isWildcardMatch(path);
            case SUFFIX_GLOB:
                return path.regionMatches((path.length() - specLength) + 1, pathSpec, 1, specLength - 1);
            case DEFAULT:
                return true;
            default:
                return false;
        }
    }
}
