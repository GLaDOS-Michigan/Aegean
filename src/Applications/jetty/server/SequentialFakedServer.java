package Applications.jetty.server;

import Applications.jetty.eve_connector.SequentialJettyEveConnector;
import Applications.tpcw_new.request_player.RequestPlayerUtils;
import Applications.tpcw_servlet.TPCW_Database;
import BFT.generalcp.GeneralCP;

/**
 * Created by remzi on 8/29/17.
 */
public class SequentialFakedServer {
    private SequentialJettyEveConnector jettyEveConnector;

    public SequentialFakedServer(String membershipFile, int id, String clientMembershipFile, int noOfClient, int subId,
                                 String logPath, String snapshotPath) {
        RequestPlayerUtils.initProperties(null);
        System.err.println("Reached constructing method entry in sequential mode");
        GeneralCP generalCP = new GeneralCP(id, membershipFile, logPath, snapshotPath, true);

        this.jettyEveConnector = new SequentialJettyEveConnector(generalCP, generalCP.getShimParameters(), snapshotPath);

        generalCP.setupApplication(jettyEveConnector);

        TPCW_Database.setGeneralCP(generalCP);
        TPCW_Database.createEveH2Connector(jettyEveConnector, clientMembershipFile, noOfClient, subId);
    }

    private void run() {
        while (true) {
            try {
                Thread.sleep(100000);
            } catch (Exception e) {
            }
        }
    }

    public static void main(String... args) throws Exception {
        int id = Integer.parseInt(args[1]);
        String membershipFile = args[0];

        String clientMembershipFile = args[2];
        int noOfClient = Integer.parseInt(args[3]);
        int subId = Integer.parseInt(args[4]);
        String logPath = args[5];
        String snapshotPath = args[6];

        SequentialFakedServer s = new SequentialFakedServer(membershipFile, id, clientMembershipFile, noOfClient, subId,
                                                            logPath, snapshotPath);
        s.run();
    }
}
