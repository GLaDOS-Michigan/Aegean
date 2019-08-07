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

package Applications.jetty.server;

import Applications.jetty.eve_connector.JettyEveConnector;
import Applications.tpcw_new.request_player.RequestPlayerUtils;
import Applications.tpcw_servlet.TPCW_Database;
import BFT.exec.ExecBaseNode;

/* ------------------------------------------------------------ */

/**
 * Jetty HTTP Servlet Server.
 * This class is the main class for the Jetty HTTP Servlet server.
 * It aggregates Connectors (HTTP request receivers) and request Handlers.
 * The server is itself a handler and a ThreadPool.  Connectors use the ThreadPool methods
 * to run jobs that will eventually call the handle method.
 */
public class FakedServer {

    transient private JettyEveConnector jeConnector;

    public FakedServer(String membershipFile, int id, String clientMembershipFile, int noOfClient, int subId) {
        RequestPlayerUtils.initProperties(null);
        System.err.println("Reached constructing method entry in parallel mode");
        ExecBaseNode exec = new ExecBaseNode(membershipFile, id, true /* isMiddle = true */);

        this.jeConnector = new JettyEveConnector(exec);

        exec.start(this.jeConnector, this.jeConnector);
        int numClients = exec.getNumClients();
        System.err.println(numClients);
        // Hack
        TPCW_Database.setExecBaseNode(exec);
        TPCW_Database.createEveH2Connector(this.jeConnector, clientMembershipFile, numClients, subId);
    }

    private void run() {
        while (true) {
            try {
                Thread.sleep(100000);
            } catch (Exception e) {
            }
        }
    }

    /* ------------------------------------------------------------ */
    public static void main(String... args) throws Exception {
        int id = Integer.parseInt(args[1]);
        String membershipFile = args[0];

        String clientMembershipFile = args[2];
        int noOfClient = Integer.parseInt(args[3]);
        int subId = Integer.parseInt(args[4]);
        FakedServer s = new FakedServer(membershipFile, id, clientMembershipFile, noOfClient, subId);

        s.run();
    }
}
