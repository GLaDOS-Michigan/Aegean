package Applications.tpcw_new.request_player.remote;

import Applications.tpcw_new.request_player.DBStatements;
import Applications.tpcw_new.request_player.Request;
import Applications.tpcw_new.request_player.RequestPlayerUtils;
import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/* Request player: replay a request trace coming from log4j logger in TPCW_Database */
public class Client {

    private static int maxNoOfRequests;
    /*
     * This hash table maps an interaction ID to a hash table which maps a
     * transaction ID to a list of Requests
     */
    private Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> allInteractions;

    /* Iterator over the keys of allInteractions */
    private Iterator<Integer> iidIterator;

    /* statistics for computing the throughput */
    private int nbRequests = 0;
    private int nbTransactions = 0;
    private int nbTransactions2 = 0;
    private int nbInteractions = 0;

    private int nbCommit = 0;
    private int nbRollbacks = 0;
    private int nbErrors = 0;

    private int replayFinished = 0;
    private boolean mainFinished = false;

    private DBStatements dbStatement;

    /*
     * main. Creates a new RequestPlayer Usage: RequestPlayer <nb
     * Clients> <requests_file> <server_address> <server_port>
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage java Client <membership> <id> <maxNoOfRequests>");
            return;
        }
        maxNoOfRequests = Integer.parseInt(args[2]);
        RequestPlayerUtils.initProperties(null);

        System.out.println("The server is " + RequestPlayerUtils.serverAddr
                + ":" + RequestPlayerUtils.serverPort);
        System.out.println("Starting request player with "
                + RequestPlayerUtils.nbClients + " clients and file "
                + RequestPlayerUtils.requestsFile);

        new Client(args[0], Integer.parseInt(args[1]));
    }

    /*
     * Constructor of RequestPlayer. Get requests, launch clients, and print
     * stats
     */
    public Client(String membership, int id) {
        dbStatement = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);

        // read file
        allInteractions = new Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>>();
        readFile(id);

        initializeInteractionsIterator();

        System.out.println("Creating " + RequestPlayerUtils.nbClients
                + " threads");
        ClientThread[] threads = new ClientThread[RequestPlayerUtils.nbClients];

        //for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
        //{
        threads[0] = new ClientThread(membership, id, this);
        //}

        System.out.println("Starting threads");
        long start = System.currentTimeMillis();
        //for (int i = 0; i < RequestPlayerUtils.nbClients; i++)
        //{
        threads[0].start();
        //}

        wait4ReplayEnd();

        long end = System.currentTimeMillis();

        System.out.println("=== End of replay! ===");

        // print stats
        printStats();

        // compute throughput
        printThroughput(end - start);
        System.exit(0);

    }

    private void readFile(int id) {
        BufferedReader reader;
        String line;

        nbRequests = 0;
        nbTransactions = 0;
        nbInteractions = 0;

        try {
            reader = new BufferedReader(new FileReader(
                    RequestPlayerUtils.requestsFile + id));

            while ((line = reader.readLine()) != null) {
                Request r = new Request(line, dbStatement);
                nbRequests++;

                Integer tid = new Integer(r.getTransactionId());
                Integer iid = new Integer(r.getInteractionId());

                Hashtable<Integer, ArrayList<Request>> oneInteraction = allInteractions
                        .get(iid);

                if (oneInteraction == null) {
                    oneInteraction = new Hashtable<Integer, ArrayList<Request>>();
                    allInteractions.put(iid, oneInteraction);
                    nbInteractions++;
                }

                // get the transaction of this request
                ArrayList<Request> transaction = oneInteraction.get(tid);

                if (transaction == null) {
                    transaction = new ArrayList<Request>();
                    oneInteraction.put(tid, transaction);
                    nbTransactions++;
                }

                // now we can add the request
                transaction.add(r);
            }

            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("=== Trace file statistics ===");
        System.out.println("Number of requests: " + nbRequests);
        System.out.println("Number of transactions: " + nbTransactions);
        System.out.println("Number of interactions: " + nbInteractions);
    }

	/*
     * private void initializeBrowsingSessionIterator() { Vector<Integer>
	 * vectorOfKeys = new Vector<Integer>( allBrowsingSessions.keySet());
	 * Collections.sort(vectorOfKeys); bsidIterator = vectorOfKeys.iterator(); }
	 */

    private void initializeInteractionsIterator() {
        Vector<Integer> vectorOfKeys = new Vector<Integer>(allInteractions
                .keySet());
        Collections.sort(vectorOfKeys);
        iidIterator = vectorOfKeys.iterator();
    }

    // public synchronized Hashtable<Integer, Hashtable<Integer,
    // ArrayList<Request>>> getBrowsingSession()
    public synchronized Hashtable<Integer, ArrayList<Request>> getInteraction() {
        if (iidIterator.hasNext()) {
            return allInteractions.remove(iidIterator.next());
        } else {
            return null;
        }
    }

    public synchronized void replayFinished() {
        replayFinished++;
        notifyAll();
    }

    public synchronized void wait4ReplayEnd() {
        while (replayFinished < RequestPlayerUtils.nbClients) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public synchronized void setMainEnd(boolean b) {
        mainFinished = b;
    }

    public synchronized boolean mainEnd() {
        return mainFinished;
    }

    public synchronized void addTransaction() {
        nbTransactions2++;
    }

    public synchronized void addCommit() {
        nbCommit++;
    }

    public synchronized void addRollback() {
        nbRollbacks++;
    }

    public synchronized void addError() {
        nbErrors++;
    }

    /* duration: a time in ms, of type long */
    private void printThroughput(long duration) {
        double runningTime = duration / 1000.0;

        System.out.println("Total running time: " + runningTime + "s");
        System.out.println("Number of requests / second: "
                + (nbRequests / runningTime));
        System.out.println("Number of transactions / second: "
                + (nbTransactions / runningTime));
        System.out.println("Number of interactions / second: "
                + (nbInteractions / runningTime));
    }

    private void printStats() {
        System.out.println("Number of requests: " + nbRequests);
        System.out.println("Number of transactions: " + nbTransactions);
        if (nbTransactions != nbTransactions2) {
            System.out
                    .println("Warning! The number of transactions in the trace"
                            + "file is not the same as the number of transactions during"
                            + "the replay: " + nbTransactions + " != "
                            + nbTransactions2);
        }
        System.out.println("\tNumber of commits: " + nbCommit);
        System.out.println("\tNumber of rollbacks: " + nbRollbacks);
        System.out.println("\tNumber of errors: " + nbErrors);
        System.out.println("Number of interactions: " + nbInteractions);
    }

    class ClientThread extends Thread implements ClientGlueInterface {
        private int id;
        private Client father;

        private ClientShimBaseNode clientShim;

        public ClientThread(String membership, int id, Client father) {
            super("RequestPlayerClientThread" + id);

            this.father = father;
            this.id = id;


            clientShim = new ClientShimBaseNode(membership, id);
            clientShim.setGlue(this);
            NettyTCPSender sendNet = new NettyTCPSender(clientShim.getParameters(), clientShim.getMembership(), 1);
            clientShim.setNetwork(sendNet);

            Role[] roles = new Role[3];
            roles[0] = Role.ORDER;
            roles[1] = Role.EXEC;
            roles[2] = Role.FILTER;

            PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(clientShim);
            NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                    clientShim.getMembership(), ptnq, 1);

            clientShim.start();


        }

        @Override
        public void brokenConnection() {
            throw new RuntimeException("Not implemented yet");
        }

        @Override
        public void returnReply(byte[] reply) {
            throw new RuntimeException("Not implemented yet");
        }

        @Override
        public byte[] canonicalEntry(byte[][] options) {
            byte[] result = null;
            for (int i = 0; i < options.length; i++) {
                if (options[i] == null)
                    continue;
                if (result == null) {
                    result = options[i];
                    continue;
                }
                if (!Arrays.equals(result, options[i])) {
                    System.out.println("Unmatched replies");
                    return null;
                }
            }
            if (result == null)
                System.out.println("Result is null");
            return result;
        }


        /* launch the client */
        public void run() {
            // System.out.println("bsid\tiid\tid\tmethod name");

            while (true) {
                // get interaction
                Hashtable<Integer, ArrayList<Request>> interaction = father
                        .getInteraction();

                if (interaction == null) {
                    break;
                }

                // execute interaction
                executeInteraction(interaction);
                if (reqIndex >= maxNoOfRequests)
                    break;
            }

            // System.out.println("Thread " + threadId + " is ending");
            father.replayFinished();
        }

        /* execute an interaction */
        private void executeInteraction(
                Hashtable<Integer, ArrayList<Request>> interaction) {
            Vector<Integer> vectorOfKeys = new Vector<Integer>(interaction
                    .keySet());
            Collections.sort(vectorOfKeys);
            Iterator<Integer> it = vectorOfKeys.iterator();

            while (it.hasNext()) {
                ArrayList<Request> transaction = interaction.get(it.next());
                executeTransaction(transaction);
            }
        }

        int reqIndex = 0;

        /* execute a transaction, ie a set of Requests before a commit */
        private void executeTransaction(ArrayList<Request> transaction) {
            // create a new TransactionMessage
            TransactionMessage tm = new TransactionMessage(transaction);
            //if(tm.getStatementCode(0)!=8&&tm.getStatementCode(0)!=14)
            //    return;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(tm);
                oos.flush();

                byte[] data = bos.toByteArray();

                byte[] buffer = null;
                long startTime = System.currentTimeMillis();
                buffer = clientShim.execute(data);
                long endTime = System.currentTimeMillis();
                if (reqIndex < maxNoOfRequests && id > 0) {
                    System.out.println("#req" + reqIndex + " " + startTime + " " + endTime + " " + id);
                }
                reqIndex++;

                father.addTransaction();

                // IN THE MEAN TIME THE INTERACTION IS BEING EXECUTED

				/*
				 * possible values of r: 0: commit 1: rollback 2: error
				 */
                int r = ByteBuffer.wrap(buffer).getInt();
                //System.out.println("r = "+r);
                if (r == 0) {
                    father.addCommit();
                } else if (r == 1) {
                    father.addRollback();
                } else {
                    father.addError();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* source: http://snippets.dzone.com/posts/show/93 */
        public final byte[] intToByteArray(int value) {
            return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16),
                    (byte) (value >>> 8), (byte) value};
        }
    }
}
