package Applications.jetty.eve_task;

import Applications.jetty.eve_connector.JettyEveConnector;
import Applications.jetty.http_servlet_wrapper.FakedHttpServletRequest;
import Applications.jetty.http_servlet_wrapper.FakedHttpServletResponse;
import Applications.tpcw_servlet.TPCW_servlet;
import BFT.exec.Info;
import BFT.exec.RequestInfo;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author lcosvse
 */

/*
 * JettyEveTask is created by JettyEveConnector -- when receiving requests from Eve interface,
 * then put onto original Jetty thread pool. 
 */

public class JettyEveTask {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private static String servletFlag = "servlet/";
    private Object servletObject;

    /*
     * Parse the request string, construct servlet handler
     */
    private void extractServlet(String request) {
        int servletIndex = request.indexOf(servletFlag);
        if (servletIndex < 0)
            throw new RuntimeException("Cannot find servlet name!");

        int endIndex1 = request.indexOf(";");
        int endIndex2 = request.indexOf("?");

        int endIndex = request.length();

        if (endIndex1 > 0 && endIndex2 > 0)
            endIndex = Math.min(endIndex1, endIndex2);
        else if (endIndex1 > 0)
            endIndex = endIndex1;
        else if (endIndex2 > 0)
            endIndex = endIndex2;

        String servletName = request.substring(servletIndex + servletFlag.length(), endIndex);

        try {
            Class<?> c = Class.forName("Applications.tpcw_servlet." + servletName);

            try {
                this.servletObject = c.newInstance();
            } catch (InstantiationException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }

        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] Servlet class <" + servletName + "> not found");
            return;
        }

    }

    public JettyEveTask(String request, JettyEveConnector connector, Info requestInfo) {
        // 1. By reflecting, instantiate handler servlet class & method
        extractServlet(request);

        // Construct (faked)HttpServletRequest/Response
        this.request = new FakedHttpServletRequest(request, requestInfo, connector);
        this.response = new FakedHttpServletResponse(requestInfo, connector);
    }

    public void run() {
        try {
            ((TPCW_servlet) this.servletObject).doGet(this.request, this.response);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ServletException e) {
            e.printStackTrace();
        }
    }

}
