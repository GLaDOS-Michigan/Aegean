package Applications.jetty.http_servlet_wrapper;

import Applications.jetty.eve_connector.JettyEveConnector;
import BFT.exec.Info;
import BFT.exec.RequestInfo;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lcosvse
 */
public class FakedHttpServletRequest implements HttpServletRequest {

    private JettyEveConnector connector;
    private Map<String, String> attributes;
    private String sessionID;

    private Info requestInfo;

    private static String sessionID_flag = "jsessionid=";

    private void extractAttributes(String request) {
        int splitter = request.indexOf('?');

        if (splitter < 0)
            return;

        String attrStr = request.substring(splitter + 1);
        String[] keyValues = attrStr.split("&");

        for (String item : keyValues) {
            if (item.indexOf('=') < 0) continue;
            String[] token = item.split("=");

            assert (token.length > 0);

            if (token.length > 1)
                this.attributes.put(token[0], formalizeString(token[1]));
            else
                this.attributes.put(token[0], "");
        }
    }

    private String formalizeString(String attr) {

        //System.err.println("String before formalization: " + attr);
        // TODO: OPTIMIZE THE ALGORITHM PLEASE!!!!
        assert (HttpUtils.keys.length == HttpUtils.values.length);

        int cur = 0;
        String res = "";
        while (attr.length() - cur >= 3) {
            if (attr.charAt(cur) == '%') {
                String token = attr.substring(cur, cur + 3).toUpperCase();
                if (HttpUtils.replace.containsKey(token))
                    res = res + HttpUtils.replace.get(token);
                else {
                    res = res + token;
                    System.err.println("@#$%^&*() Weird: " + token);
                }
                cur += 3;
            } else {
                res = res + attr.charAt(cur);
                cur++;
            }
        }
        res = res + attr.substring(cur);
        //System.err.println("String after formalization: " + res);
        return res;
    }

    private void extractSessionID(String request) {
        int sessionFlagIndex = request.indexOf(sessionID_flag);

        if (sessionFlagIndex < 0) return;

        int endIndex = request.indexOf("?");
        if (endIndex < 0) endIndex = request.length();

        this.sessionID = request.substring(sessionFlagIndex + sessionID_flag.length(), endIndex);
    }

    public FakedHttpServletRequest(String request, Info requestInfo, JettyEveConnector connector) {
        this.connector = connector;
        this.attributes = new ConcurrentHashMap<String, String>();
        this.sessionID = null;
        this.requestInfo = requestInfo;
        extractAttributes(request);
        extractSessionID(request);

        if (this.sessionID == null) {
            this.sessionID = SessionIDGenerator.generateSessionId(this.requestInfo);
        }
    }

    public static void main(String[] args) {
        HttpUtils.initMap();
        new FakedHttpServletRequest("http://128.83.122.190:8389/servlet/TPCW_buy_request_servlet;jsessionidsngodcnaegkybsvkjeiqbcxuvojmuxhu?RETURNING_FLAG=N&FNAME=x%2CrV2c9cDI%25&LNAME=Kh%7EvVi%7B%3F&STREET_1=%7E%3Fnlg%2F5B+tec3C%28%40P0LKt&STREET_2=0u-%3D7%24Kp*%28%5E%5E%23jzclkR1w.&CITY=O9R%21QaNUG7r2fD%5BEodg5%5E9&STATE=3YG%3BD0S&ZIP=y531g%7D&COUNTRY=Taiwan&PHONE=8579702893842833&EMAIL=ooxf%2836%40%29D.com&BIRTHDATE=23%2f4%2f2006&DATA=Ny%3BU%3B-ZDH%7C8l0Xq%5Fb%7EZi%3Ba%40O5v5F9V%3A%21x%5E9cNK86j%3B%5E%7Ed%3A6%21p%23pm%3AU%5Exp%3F%3D%5EWv6I-%2B6l5EY+3n%3Bz5jdAemw%25dRWtk4Xg%2C-%23-%21y%5D%3D%3Fqo%3Aq%3FJ.6uG%7EptI%5BC+%21excd9Tt9Jbx1QF9%21MhO%7EBvNk%25%5Dyao%7E%5E%7D%24SBuV92r7xpkIUMlh%3D%5DlD%7Cd%7D*e4%5F%3BkM%7C.B64J%3F&SHOPPING_ID=0",
                null, null);
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Object getAttribute(String key) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String getCharacterEncoding() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getContentLength() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public long getContentLengthLong() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getContentType() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public DispatcherType getDispatcherType() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getLocalAddr() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getLocalName() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getLocalPort() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Locale getLocale() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getParameter(String key) {
        if (this.attributes.containsKey(key))
            return this.attributes.get(key);
        else
            return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Enumeration<String> getParameterNames() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String[] getParameterValues(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getProtocol() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public BufferedReader getReader() throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRealPath(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRemoteAddr() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRemoteHost() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getRemotePort() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public RequestDispatcher getRequestDispatcher(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getScheme() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getServerName() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getServerPort() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public ServletContext getServletContext() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isAsyncStarted() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isAsyncSupported() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isSecure() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void removeAttribute(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setAttribute(String arg0, Object arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setCharacterEncoding(String arg0)
            throws UnsupportedEncodingException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public AsyncContext startAsync(ServletRequest arg0, ServletResponse arg1)
            throws IllegalStateException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean authenticate(HttpServletResponse arg0) throws IOException,
            ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String changeSessionId() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getAuthType() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getContextPath() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Cookie[] getCookies() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public long getDateHeader(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getHeader(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Enumeration<String> getHeaderNames() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Enumeration<String> getHeaders(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getIntHeader(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getMethod() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Part getPart(String arg0) throws IOException, ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getPathInfo() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getPathTranslated() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getQueryString() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRemoteUser() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRequestURI() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public StringBuffer getRequestURL() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public String getRequestedSessionId() {
        return this.sessionID;
    }

    @Override
    public String getServletPath() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public HttpSession getSession() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public HttpSession getSession(boolean createOnNull) {
        return this.connector.getSessionManager().getSession(sessionID, createOnNull);
    }

    @Override
    public Principal getUserPrincipal() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean isUserInRole(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void login(String arg0, String arg1) throws ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void logout() throws ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> arg0)
            throws IOException, ServletException {
        throw new RuntimeException("Interface method not implemented.");

    }

}
