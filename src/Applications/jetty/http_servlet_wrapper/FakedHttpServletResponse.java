package Applications.jetty.http_servlet_wrapper;

import Applications.jetty.eve_connector.JettyEveConnector;
import BFT.exec.Info;
import BFT.exec.RequestInfo;
import merkle.MerkleTreeInstance;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

/**
 * @author lcosvse
 */

public class FakedHttpServletResponse implements HttpServletResponse {

    private PrintWriter writer = null;
    private JettyEveConnector connector;
    private Info requestInfo;
    private String encodedSessionId;

    private FakedHttpServletResponse() {
    }

    public FakedHttpServletResponse(Info requestInfo, JettyEveConnector connector) {
        this.connector = connector;
        this.requestInfo = requestInfo;
        this.writer = new EvePrintWriter(this.connector, this.requestInfo);
        this.encodedSessionId = null;
    }

    private String replaceWithEscape(String plainText) {
        String res = "";
        int len = plainText.length();
        for (int i = 0; i < len; i++) {
            String ch = Character.toString(plainText.charAt(i));
            if (HttpUtils.encode.containsKey(ch)) {
                res = res + HttpUtils.encode.get(ch);
            } else
                res = res + ch;
        }
        return res;
    }

    @Override
    public void flushBuffer() throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public int getBufferSize() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String getCharacterEncoding() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String getContentType() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public Locale getLocale() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return this.writer;
    }

    @Override
    public boolean isCommitted() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public void reset() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void resetBuffer() {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setBufferSize(int arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setCharacterEncoding(String arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setContentLength(int arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setContentLengthLong(long arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setContentType(String contentType) {
        // do nothing? 
    }

    @Override
    public void setLocale(Locale arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void addCookie(Cookie arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void addDateHeader(String arg0, long arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void addHeader(String arg0, String arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void addIntHeader(String arg0, int arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public boolean containsHeader(String arg0) {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String encodeRedirectURL(String arg0) {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String encodeRedirectUrl(String arg0) {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public String encodeURL(String url) {
        return encodeUrl(url);
    }

    @Override
    public String encodeUrl(String url) {

        if (this.encodedSessionId == null) {
            MerkleTreeInstance.update(this);
            this.encodedSessionId = SessionIDGenerator.generateSessionId(this.requestInfo);
        }

        String res = null;
        int indexOfQuestionMark = url.indexOf('?');
        if (indexOfQuestionMark == -1) {
            res = url + ";jsessionid=" + this.encodedSessionId;
        } else {
            res = url.substring(0, indexOfQuestionMark) + ";jsessionid=" + this.encodedSessionId + replaceWithEscape(url.substring(indexOfQuestionMark));
        }
        return res;
    }

    @Override
    public String getHeader(String arg0) {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public Collection<String> getHeaderNames() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public Collection<String> getHeaders(String arg0) {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public int getStatus() {
        throw new RuntimeException("Interface method not implemented.");
    }

    @Override
    public void sendError(int arg0) throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void sendError(int arg0, String arg1) throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void sendRedirect(String arg0) throws IOException {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setDateHeader(String arg0, long arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setHeader(String arg0, String arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setIntHeader(String arg0, int arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setStatus(int arg0) {
        throw new RuntimeException("Interface method not implemented.");

    }

    @Override
    public void setStatus(int arg0, String arg1) {
        throw new RuntimeException("Interface method not implemented.");

    }

}
