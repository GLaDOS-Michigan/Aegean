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

package Applications.jetty.servlet;

import Applications.jetty.server.*;
import Applications.jetty.server.handler.ContextHandler;
import Applications.jetty.server.handler.StatisticsHandler;
import Applications.jetty.util.log.Log;
import Applications.jetty.util.log.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * StatisticsServlet
 */
public class StatisticsServlet extends HttpServlet {
    private static final Logger LOG = Log.getLogger(StatisticsServlet.class);

    boolean _restrictToLocalhost = true; // defaults to true
    private StatisticsHandler _statsHandler;
    private MemoryMXBean _memoryBean;
    private Connector[] _connectors;


    /**
     * @see javax.servlet.GenericServlet#init()
     */
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        ContextHandler.Context scontext = (ContextHandler.Context) context;
        Server _server = scontext.getContextHandler().getServer();

        Handler handler = _server.getChildHandlerByClass(StatisticsHandler.class);

        if (handler != null) {
            _statsHandler = (StatisticsHandler) handler;
        } else {
            LOG.warn("Statistics Handler not installed!");
            return;
        }

        _memoryBean = ManagementFactory.getMemoryMXBean();
        _connectors = _server.getConnectors();

        if (getInitParameter("restrictToLocalhost") != null) {
            _restrictToLocalhost = "true".equals(getInitParameter("restrictToLocalhost"));
        }
    }


    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void doPost(HttpServletRequest sreq, HttpServletResponse sres) throws ServletException, IOException {
        doGet(sreq, sres);
    }


    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (_statsHandler == null) {
            LOG.warn("Statistics Handler not installed!");
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (_restrictToLocalhost) {
            if (!isLoopbackAddress(req.getRemoteAddr())) {
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
        }

        String wantXml = req.getParameter("xml");
        if (wantXml == null)
            wantXml = req.getParameter("XML");

        if (wantXml != null && "true".equalsIgnoreCase(wantXml)) {
            sendXmlResponse(resp);
        } else {
            sendTextResponse(resp);
        }

    }

    private boolean isLoopbackAddress(String address) {
        try {
            InetAddress addr = InetAddress.getByName(address);
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            LOG.warn("Warning: attempt to access statistics servlet from " + address, e);
            return false;
        }
    }

    private void sendXmlResponse(HttpServletResponse response) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("<statistics>\n");

        sb.append("  <requests>\n");
        sb.append("    <statsOnMs>").append(_statsHandler.getStatsOnMs()).append("</statsOnMs>\n");

        sb.append("    <requests>").append(_statsHandler.getRequests()).append("</requests>\n");
        sb.append("    <requestsActive>").append(_statsHandler.getRequestsActive()).append("</requestsActive>\n");
        sb.append("    <requestsActiveMax>").append(_statsHandler.getRequestsActiveMax()).append("</requestsActiveMax>\n");
        sb.append("    <requestsTimeTotal>").append(_statsHandler.getRequestTimeTotal()).append("</requestsTimeTotal>\n");
        sb.append("    <requestsTimeMean>").append(_statsHandler.getRequestTimeMean()).append("</requestsTimeMean>\n");
        sb.append("    <requestsTimeMax>").append(_statsHandler.getRequestTimeMax()).append("</requestsTimeMax>\n");
        sb.append("    <requestsTimeStdDev>").append(_statsHandler.getRequestTimeStdDev()).append("</requestsTimeStdDev>\n");

        sb.append("    <dispatched>").append(_statsHandler.getDispatched()).append("</dispatched>\n");
        sb.append("    <dispatchedActive>").append(_statsHandler.getDispatchedActive()).append("</dispatchedActive>\n");
        sb.append("    <dispatchedActiveMax>").append(_statsHandler.getDispatchedActiveMax()).append("</dispatchedActiveMax>\n");
        sb.append("    <dispatchedTimeTotalMs>").append(_statsHandler.getDispatchedTimeTotal()).append("</dispatchedTimeTotalMs>\n");
        sb.append("    <dispatchedTimeMeanMs>").append(_statsHandler.getDispatchedTimeMean()).append("</dispatchedTimeMeanMs>\n");
        sb.append("    <dispatchedTimeMaxMs>").append(_statsHandler.getDispatchedTimeMax()).append("</dispatchedTimeMaxMs>\n");
        sb.append("    <dispatchedTimeStdDevMs>").append(_statsHandler.getDispatchedTimeStdDev()).append("</dispatchedTimeStdDevMs>\n");

        sb.append("    <asyncRequests>").append(_statsHandler.getAsyncRequests()).append("</asyncRequests>\n");
        sb.append("    <requestsSuspended>").append(_statsHandler.getAsyncRequestsWaiting()).append("</requestsSuspended>\n");
        sb.append("    <requestsSuspendedMax>").append(_statsHandler.getAsyncRequestsWaitingMax()).append("</requestsSuspendedMax>\n");
        sb.append("    <requestsResumed>").append(_statsHandler.getAsyncDispatches()).append("</requestsResumed>\n");
        sb.append("    <requestsExpired>").append(_statsHandler.getExpires()).append("</requestsExpired>\n");
        sb.append("  </requests>\n");

        sb.append("  <responses>\n");
        sb.append("    <responses1xx>").append(_statsHandler.getResponses1xx()).append("</responses1xx>\n");
        sb.append("    <responses2xx>").append(_statsHandler.getResponses2xx()).append("</responses2xx>\n");
        sb.append("    <responses3xx>").append(_statsHandler.getResponses3xx()).append("</responses3xx>\n");
        sb.append("    <responses4xx>").append(_statsHandler.getResponses4xx()).append("</responses4xx>\n");
        sb.append("    <responses5xx>").append(_statsHandler.getResponses5xx()).append("</responses5xx>\n");
        sb.append("    <responsesBytesTotal>").append(_statsHandler.getResponsesBytesTotal()).append("</responsesBytesTotal>\n");
        sb.append("  </responses>\n");

        sb.append("  <connections>\n");
        for (Connector connector : _connectors) {
            sb.append("    <connector>\n");
            sb.append("      <name>").append(connector.getClass().getName()).append("@").append(connector.hashCode()).append("</name>\n");
            sb.append("      <protocols>\n");
            for (String protocol : connector.getProtocols())
                sb.append("      <protocol>").append(protocol).append("</protocol>\n");
            sb.append("      </protocols>\n");

            ConnectorStatistics connectorStats = null;

            if (connector instanceof AbstractConnector)
                connectorStats = ((AbstractConnector) connector).getBean(ConnectorStatistics.class);
            if (connectorStats == null)
                sb.append("      <statsOn>false</statsOn>\n");
            else {
                sb.append("      <statsOn>true</statsOn>\n");
                sb.append("      <connections>").append(connectorStats.getConnections()).append("</connections>\n");
                sb.append("      <connectionsOpen>").append(connectorStats.getConnectionsOpen()).append("</connectionsOpen>\n");
                sb.append("      <connectionsOpenMax>").append(connectorStats.getConnectionsOpenMax()).append("</connectionsOpenMax>\n");
                sb.append("      <connectionsDurationTotal>").append(connectorStats.getConnectionsDurationTotal()).append("</connectionsDurationTotal>\n");
                sb.append("      <connectionsDurationMean>").append(connectorStats.getConnectionsDurationMean()).append("</connectionsDurationMean>\n");
                sb.append("      <connectionsDurationMax>").append(connectorStats.getConnectionsDurationMax()).append("</connectionsDurationMax>\n");
                sb.append("      <connectionsDurationStdDev>").append(connectorStats.getConnectionsDurationStdDev()).append("</connectionsDurationStdDev>\n");
                sb.append("      <messagesIn>").append(connectorStats.getMessagesIn()).append("</messagesIn>\n");
                sb.append("      <messagesOut>").append(connectorStats.getMessagesIn()).append("</messagesOut>\n");
                sb.append("      <elapsedMs>").append(connectorStats.getStartedMillis()).append("</elapsedMs>\n");
            }
            sb.append("    </connector>\n");
        }
        sb.append("  </connections>\n");

        sb.append("  <memory>\n");
        sb.append("    <heapMemoryUsage>").append(_memoryBean.getHeapMemoryUsage().getUsed()).append("</heapMemoryUsage>\n");
        sb.append("    <nonHeapMemoryUsage>").append(_memoryBean.getNonHeapMemoryUsage().getUsed()).append("</nonHeapMemoryUsage>\n");
        sb.append("  </memory>\n");

        sb.append("</statistics>\n");

        response.setContentType("text/xml");
        PrintWriter pout = response.getWriter();
        pout.write(sb.toString());
    }


    /**
     * @param response
     * @throws IOException
     */
    private void sendTextResponse(HttpServletResponse response) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(_statsHandler.toStatsHTML());

        sb.append("<h2>Connections:</h2>\n");
        for (Connector connector : _connectors) {
            sb.append("<h3>").append(connector.getClass().getName()).append("@").append(connector.hashCode()).append("</h3>");
            sb.append("Protocols:");
            for (String protocol : connector.getProtocols())
                sb.append(protocol).append("&nbsp;");
            sb.append("    <br />\n");

            ConnectorStatistics connectorStats = null;

            if (connector instanceof AbstractConnector)
                connectorStats = ((AbstractConnector) connector).getBean(ConnectorStatistics.class);

            if (connectorStats != null) {
                sb.append("Statistics gathering started ").append(connectorStats.getStartedMillis()).append("ms ago").append("<br />\n");
                sb.append("Total connections: ").append(connectorStats.getConnections()).append("<br />\n");
                sb.append("Current connections open: ").append(connectorStats.getConnectionsOpen()).append("<br />\n");
                ;
                sb.append("Max concurrent connections open: ").append(connectorStats.getConnectionsOpenMax()).append("<br />\n");
                sb.append("Total connections duration: ").append(connectorStats.getConnectionsDurationTotal()).append("<br />\n");
                sb.append("Mean connection duration: ").append(connectorStats.getConnectionsDurationMean()).append("<br />\n");
                sb.append("Max connection duration: ").append(connectorStats.getConnectionsDurationMax()).append("<br />\n");
                sb.append("Connection duration standard deviation: ").append(connectorStats.getConnectionsDurationStdDev()).append("<br />\n");
                sb.append("Total messages in: ").append(connectorStats.getMessagesIn()).append("<br />\n");
                sb.append("Total messages out: ").append(connectorStats.getMessagesOut()).append("<br />\n");
            } else {
                sb.append("Statistics gathering off.\n");
            }

        }

        sb.append("<h2>Memory:</h2>\n");
        sb.append("Heap memory usage: ").append(_memoryBean.getHeapMemoryUsage().getUsed()).append(" bytes").append("<br />\n");
        sb.append("Non-heap memory usage: ").append(_memoryBean.getNonHeapMemoryUsage().getUsed()).append(" bytes").append("<br />\n");

        response.setContentType("text/html");
        PrintWriter pout = response.getWriter();
        pout.write(sb.toString());

    }
}
