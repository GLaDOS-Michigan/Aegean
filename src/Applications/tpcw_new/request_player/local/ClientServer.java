package Applications.tpcw_new.request_player.local;

import Applications.tpcw_new.request_player.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/* Request player: replay a request trace captured by log4j in TPCW_Database */
public class ClientServer {
    DataBase dataBase;

    /* to access to the statements */
    private DBStatements dbStatements = null;

    /* false until the replay is not finished */
    private boolean replayFinished = false;

    /*
     * This hash table maps an interaction ID to a hash table which maps a
     * transaction ID to a list of Requests
     */
    private Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> allInteractions;

    /* Iterator over the keys of allInteractions */
    private Iterator<Integer> iidIterator;

    /* statistics for computing the throughput */
    private int nbRequests;
    private int nbTransactions;
    private int nbInteractions;

    /*
     * main. Creates a new RequestPlayer Usage: RequestPlayer [properties_file]
     */
    public static void main(String[] args) {
        if (args.length >= 1) {
            RequestPlayerUtils.initProperties(args[0]);
        } else {
            RequestPlayerUtils.initProperties(null);
        }

        System.out.println("Starting request player with "
                + RequestPlayerUtils.nbClients + " clients and file "
                + RequestPlayerUtils.requestsFile);

        new ClientServer();
    }

    /*
     * Constructor of RequestPlayer. Get requests, launch clients, and print
     * stats
     */
    public ClientServer() {
        // initialize the statements
        dbStatements = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);

        // read file
        allInteractions = new Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>>();
        readFile();

        initializeInteractionsIterator();

        // create DB
        dataBase = (DataBase) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBClass);
        dataBase.initDB();

        System.out.println("Creating " + RequestPlayerUtils.nbClients
                + " threads");
        ClientServerThread[] threads = new ClientServerThread[RequestPlayerUtils.nbClients];

        for (int i = 0; i < RequestPlayerUtils.nbClients; i++) {
            threads[i] = new ClientServerThread(i, this);
        }

        // start monitor thread
        new MonitorThread(0, RequestPlayerUtils.monitorDelay, this).start();

        System.out.println("Starting threads");
        long start = System.currentTimeMillis();
        for (int i = 0; i < RequestPlayerUtils.nbClients; i++) {
            threads[i].start();
        }

        for (int i = 0; i < RequestPlayerUtils.nbClients; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.out.println("RequestPlayerThread " + i
                        + " has been interrupted!");
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();

        System.out.println("=== End of replay! ===");

        replayFinished = true;

        // print stats
        printStats();

        // compute throughput
        printThroughput(end - start);
    }

    private void readFile() {
        BufferedReader reader;
        String line;

        nbRequests = 0;
        nbTransactions = 0;
        nbInteractions = 0;

        try {
            reader = new BufferedReader(new FileReader(
                    RequestPlayerUtils.requestsFile));

            while ((line = reader.readLine()) != null) {
                Request r = new Request(line, dbStatements);
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

    public synchronized boolean canExecuteTransaction(
            ArrayList<Request> transaction) {
        RequestPlayerUtils.methodName method;
        boolean isRead;
        try {
            switch (RequestPlayerUtils.degreeOfParallelism) {
                case RequestPlayerUtils.allParallel:
                    return true;

                case RequestPlayerUtils.seqWritesParallelReads:
                    method = RequestPlayerUtils.string2methodName(transaction
                            .get(0).getMethod());
                    isRead = RequestPlayerUtils.methodIsRead(method);

                    if (!isRead) {
                        while (RequestPlayerUtils.currentWrite) {
                            wait();
                        }

                        RequestPlayerUtils.currentWrite = true;
                    }

                    return true;

                case RequestPlayerUtils.seqWritesParallelReadsNoWait:
                    method = RequestPlayerUtils.string2methodName(transaction
                            .get(0).getMethod());
                    isRead = RequestPlayerUtils.methodIsRead(method);

                    if (!isRead) {
                        if (RequestPlayerUtils.currentWrite) {
                            return false;
                        }

                        RequestPlayerUtils.currentWrite = true;
                    }

                    return true;

                case RequestPlayerUtils.parallelismRules:
                    System.err
                            .println("Rules parallelism not implemented yet!");
                    break;

                default:
                    System.err.println("Unknown degree of parallelism: "
                            + RequestPlayerUtils.degreeOfParallelism);
                    break;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return false;
    }

    public synchronized void transactionExecuted(ArrayList<Request> transaction) {
        switch (RequestPlayerUtils.degreeOfParallelism) {
            case RequestPlayerUtils.allParallel: {
                break;
            }

            case RequestPlayerUtils.seqWritesParallelReads:
            case RequestPlayerUtils.seqWritesParallelReadsNoWait: {
                RequestPlayerUtils.methodName method = RequestPlayerUtils
                        .string2methodName(transaction.get(0).getMethod());
                boolean isRead = RequestPlayerUtils.methodIsRead(method);

                if (!isRead) {
                    RequestPlayerUtils.currentWrite = false;
                    if (RequestPlayerUtils.degreeOfParallelism == RequestPlayerUtils.seqWritesParallelReads) {
                        notifyAll();
                    }
                }
            }
            break;

            case RequestPlayerUtils.parallelismRules: {
                System.err.println("Rules parallelism not implemented yet!");
            }
            break;

            default: {
                System.err.println("Unknown degree of parallelism: "
                        + RequestPlayerUtils.degreeOfParallelism);
            }
            break;
        }
    }

    /* duration: a time in ms, of type long */
    private void printThroughput(long duration) {
        double runningTime = duration / 1000.0;

        System.out.println("Total running time: " + runningTime + "s");
        System.out.println("Number of requests / second: "
                + (nbRequests / runningTime));
        System.out.println("Number of transactions / second: "
                + (DatabaseStats.getTransaction() / runningTime));
        System.out.println("Number of interactions / second: "
                + (nbInteractions / runningTime));
    }

    private void printStats() {
        System.out.println("Number of requests: " + nbRequests);
        System.out.println("Number of transactions: "
                + DatabaseStats.getTransaction());
        System.out.println("\tNumber of commits: " + DatabaseStats.getCommit());
        System.out.println("\tNumber of rollbacks: "
                + DatabaseStats.getRollback());
        System.out.println("\tNumber of errors: " + DatabaseStats.getErrors());
        System.out.println("\tNumber of reads: " + DatabaseStats.getReads());
        System.out.println("\tNumber of writes: " + DatabaseStats.getWrites());
        System.out.println("Number of interactions: " + nbInteractions);
    }

    public synchronized boolean replayFinished() {
        return replayFinished;
    }

    class MonitorThread extends Thread {
        private int threadId;
        private long delay;
        private ClientServer father;

        /*
         * create a new MonitorThread of id threadId which outputs statistics
         * about the BD every delay minutes
         */
        public MonitorThread(int threadId, long delay, ClientServer father) {
            super("MonitorThread" + threadId);
            this.threadId = threadId;
            this.delay = delay * 60 * 1000;
            this.father = father;
        }

        public void run() {
            int currentDuration = 0;

            System.out.println("Monitor thread " + threadId + " launched");

            while (true) {
                // print stats
                System.out.println("Monitor: " + currentDuration + " minutes");
                System.out.println("\tNumber of transactions: "
                        + DatabaseStats.getTransaction());
                System.out.println("\tNumber of commits: "
                        + DatabaseStats.getCommit());
                System.out.println("\tNumber of rollbacks: "
                        + DatabaseStats.getRollback());
                System.out.println("\tNumber of errors: "
                        + DatabaseStats.getErrors());
                System.out.println("\tNumber of reads: "
                        + DatabaseStats.getReads());
                System.out.println("\tNumber of writes: "
                        + DatabaseStats.getWrites());

                if (father.replayFinished()) {
                    break;
                }

                // sleep
                try {
                    sleep(delay);
                } catch (InterruptedException e) {
                }
                currentDuration += delay / (60 * 1000);
            }
        }
    }

    class ClientServerThread extends Thread {
        private ClientServer father;
        @SuppressWarnings("unused")
        private int threadId;
        private ConnectionStatement cs;

        public ClientServerThread(int threadId, ClientServer father) {
            super("RequestPlayerThread" + threadId);

            this.father = father;
            this.threadId = threadId;

            cs = new ConnectionStatement(father.dbStatements);
        }

        /* launch the client */
        public void run() {
            while (true) {

                // get interaction
                Hashtable<Integer, ArrayList<Request>> interaction = father
                        .getInteraction();

                if (interaction == null) {
                    break;
                }

                // execute interaction
                executeInteraction(interaction);

            }

            // System.out.println("Thread " + threadId + " is ending");
            cs.closeConnection();
        }

        /* Execute a browsing session */
        @SuppressWarnings("unused")
        private void executeBrowsingSession(
                Hashtable<Integer, Hashtable<Integer, ArrayList<Request>>> browsingSession) {
            Vector<Integer> vectorOfKeys = new Vector<Integer>(browsingSession
                    .keySet());
            Collections.sort(vectorOfKeys);
            Iterator<Integer> it = vectorOfKeys.iterator();

            while (it.hasNext()) {
                Hashtable<Integer, ArrayList<Request>> interaction = browsingSession
                        .get(it.next());
                executeInteraction(interaction);
            }
        }

        /* execute an interaction */
        private void executeInteraction(
                Hashtable<Integer, ArrayList<Request>> interaction) {
            Vector<Integer> vectorOfKeys = new Vector<Integer>(interaction
                    .keySet());
            Collections.sort(vectorOfKeys);

            int idx = 0;
            while (!interaction.isEmpty()) {
                Integer key = vectorOfKeys.get(idx % vectorOfKeys.size());
                ArrayList<Request> transaction = interaction.remove(key);

                if (father.canExecuteTransaction(transaction)) {
                    executeTransaction(transaction);
                    father.transactionExecuted(transaction);
                    vectorOfKeys.remove(idx % vectorOfKeys.size());
                } else {
                    interaction.put(key, transaction);
                    idx++;
                }
            }

			/*
			 * Iterator<Integer> it = vectorOfKeys.iterator();
			 * 
			 * while (it.hasNext()) { ArrayList<Request> transaction =
			 * interaction.get(it.next()); ArrayList<Request> transaction =
			 * executeTransaction(transaction); }
			 */
        }

        /* execute a transaction, ie a set of Requests before a commit */
        private void executeTransaction(ArrayList<Request> transaction) {
            Connection con = cs.getConnection();
            DatabaseStats.addTransaction();

            try {
                for (Request r : transaction) {
                    // Use already created prepared statements:
                    execute(cs.getPreparedStatement(r.getStatementCode()), r
                            .getStatement(), r.getArguments());
                }

                con.commit();
                DatabaseStats.addCommit();
            } catch (java.lang.Exception ex) {
                try {
                    con.rollback();
                    DatabaseStats.addRollback();
                    ex.printStackTrace();
                } catch (Exception se) {
                    DatabaseStats.addErrors();
                    System.err.println("Transaction rollback failed.");
                    se.printStackTrace();
                }
            }
        }

        /*
         * execute the PreparedStatement ps with arguments arguments
         */
        private void execute(PreparedStatement ps, String statement,
                             String arguments) throws SQLException {
            // set args
            setArguments(ps, arguments);

            // execute
            boolean isExecuteQuery = ps.execute();

            if (isExecuteQuery || statement.contains("SELECT")) {
                DatabaseStats.addRead();
            } else {
                DatabaseStats.addWrite();
            }
        }

        /*
         * execute the statement statement with arguments arguments
         */
        @SuppressWarnings("unused")
        private void execute(Connection con, String statement, String arguments)
                throws SQLException {
            // create PreparedStatement
            PreparedStatement ps = con.prepareStatement(statement);

            // set args
            setArguments(ps, arguments);

            // execute
            boolean isExecuteQuery = ps.execute();

            if (isExecuteQuery || statement.contains("SELECT")) {
                DatabaseStats.addRead();
            } else {
                DatabaseStats.addWrite();
            }
        }

        /**
         * Set the arguments in arguments in the prepared statement ps Source:
         * Sequoia's PreparedStatementSerialization.setPreparedStatement()
         * method
         */
        private void setArguments(PreparedStatement backendPS, String parameters)
                throws SQLException {
            int i = 0;
            int paramIdx = 0;

            // Set all parameters
            while ((i = parameters.indexOf(RequestPlayerUtils.START_PARAM_TAG,
                    i)) > -1) {
                paramIdx++;

                int typeStart = i + RequestPlayerUtils.START_PARAM_TAG.length();

                // Here we assume that all tags have the same length as the
                // boolean tag.
                String paramType = parameters.substring(typeStart, typeStart
                        + RequestPlayerUtils.BOOLEAN_TAG.length());
                String paramValue = parameters.substring(typeStart
                        + RequestPlayerUtils.BOOLEAN_TAG.length(), parameters
                        .indexOf(RequestPlayerUtils.END_PARAM_TAG, i));
                paramValue = replace(paramValue,
                        RequestPlayerUtils.TAG_MARKER_ESCAPE,
                        RequestPlayerUtils.TAG_MARKER);

                if (!performCallOnPreparedStatement(backendPS, paramIdx,
                        paramType, paramValue)) {
                    // invalid parameter, we want to be able to store strings
                    // like
                    // <?xml version="1.0" encoding="ISO-8859-1"?>
                    paramIdx--;
                }
                i = typeStart + paramValue.length();
            }
        }

        private boolean performCallOnPreparedStatement(
                java.sql.PreparedStatement backendPS, int paramIdx,
                String paramType, String paramValue) throws SQLException {
            // Test tags in alphabetical order (to make the code easier to read)
			/*
			 * if (paramType.equals(RequestPlayerUtils.ARRAY_TAG)) { // in case
			 * of Array, the String contains: // - integer: sql type // -
			 * string: sql type name, delimited by ARRAY_BASETYPE_NAME_SEPARATOR // -
			 * serialized object array final String commonMsg = "Failed to
			 * deserialize Array parameter of setObject()"; int baseType; String
			 * baseTypeName; // Get start and end of the type name string int
			 * startBaseTypeName =
			 * paramValue.indexOf(RequestPlayerUtils.ARRAY_BASETYPE_NAME_SEPARATOR );
			 * int endBaseTypeName =
			 * paramValue.indexOf(RequestPlayerUtils.ARRAY_BASETYPE_NAME_SEPARATOR ,
			 * startBaseTypeName + 1);
			 * 
			 * baseType = Integer.valueOf(paramValue.substring(0,
			 * startBaseTypeName)) .intValue(); baseTypeName =
			 * paramValue.substring(startBaseTypeName + 1, endBaseTypeName); //
			 * create a new string without type information in front. paramValue =
			 * paramValue.substring(endBaseTypeName + 1); // rest of the array
			 * deserialization code is copied from OBJECT_TAG
			 * 
			 * Object obj; try { byte[] decoded =
			 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue);
			 * obj = new ObjectInputStream(new ByteArrayInputStream(decoded))
			 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
			 * (SQLException) new SQLException(commonMsg + ", class not found on
			 * controller").initCause(cnfe); } catch (IOException ioe) // like
			 * for instance invalid stream header { throw (SQLException) new
			 * SQLException(commonMsg + ", I/O exception") .initCause(ioe); } //
			 * finally, construct the java.sql.array interface object using //
			 * deserialized object, and type information
			 * org.continuent.sequoia.common.protocol.Array array = new
			 * org.continuent.sequoia.common.protocol.Array( obj, baseType,
			 * baseTypeName); backendPS.setArray(paramIdx, array); } else
			 */
            if (paramType.equals(RequestPlayerUtils.BIG_DECIMAL_TAG)) {
                BigDecimal t = null;
                if (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    t = new BigDecimal(paramValue);
                }
                backendPS.setBigDecimal(paramIdx, t);
            } else if (paramType.equals(RequestPlayerUtils.BOOLEAN_TAG)) {
                backendPS.setBoolean(paramIdx, Boolean.valueOf(paramValue)
                        .booleanValue());
            } else if (paramType.equals(RequestPlayerUtils.BYTE_TAG)) {
                byte t = new Integer(paramValue).byteValue();
                backendPS.setByte(paramIdx, t);
            }
			/*
			 * else if (paramType.equals(RequestPlayerUtils.BYTES_TAG)) { /**
			 * encoded by the driver at {@link #setBytes(int, byte[])}in order
			 * to inline it in the request (no database encoding here).
			 * 
			 * byte[] t =
			 * AbstractBlobFilter.getDefaultBlobFilter().decode(paramValue);
			 * backendPS.setBytes(paramIdx, t); }
			 */
			/*
			 * else if (paramType.equals(RequestPlayerUtils.BLOB_TAG)) {
			 * ByteArrayBlob b = null; // encoded by the driver at {@link
			 * #setBlob(int, java.sql.Blob)} if
			 * (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) b = new
			 * ByteArrayBlob(AbstractBlobFilter.getDefaultBlobFilter().decode(
			 * paramValue)); backendPS.setBlob(paramIdx, b); }
			 */
			/*
			 * else if (paramType.equals(RequestPlayerUtils.CLOB_TAG)) {
			 * StringClob c = null; if
			 * (!paramValue.equals(RequestPlayerUtils.NULL_VALUE)) c = new
			 * StringClob(paramValue); backendPS.setClob(paramIdx, c); }
			 */
            else if (paramType.equals(RequestPlayerUtils.DATE_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE))
                    backendPS.setDate(paramIdx, null);
                else
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd");
                        Date t = new Date(sdf.parse(paramValue).getTime());
                        backendPS.setDate(paramIdx, t);
                    } catch (ParseException p) {
                        backendPS.setDate(paramIdx, null);
                        throw new SQLException("Couldn't format date!!!");
                    }
            } else if (paramType.equals(RequestPlayerUtils.DOUBLE_TAG)) {
                backendPS.setDouble(paramIdx, Double.valueOf(paramValue)
                        .doubleValue());
            } else if (paramType.equals(RequestPlayerUtils.FLOAT_TAG)) {
                backendPS.setFloat(paramIdx, Float.valueOf(paramValue)
                        .floatValue());
            } else if (paramType.equals(RequestPlayerUtils.INTEGER_TAG)) {
                backendPS.setInt(paramIdx, Integer.valueOf(paramValue)
                        .intValue());
            } else if (paramType.equals(RequestPlayerUtils.LONG_TAG)) {
                backendPS.setLong(paramIdx, Long.valueOf(paramValue)
                        .longValue());
            } else if (paramType.equals(RequestPlayerUtils.NULL_VALUE)) {
                backendPS.setNull(paramIdx, Integer.valueOf(paramValue)
                        .intValue());
            }
			/*
			 * else if (paramType.equals(RequestPlayerUtils.OBJECT_TAG)) { if
			 * (paramValue.equals(RequestPlayerUtils.NULL_VALUE))
			 * backendPS.setObject(paramIdx, null); else { final String
			 * commonMsg = "Failed to deserialize object parameter of
			 * setObject()"; Object obj; try { byte[] decoded =
			 * AbstractBlobFilter.getDefaultBlobFilter().decode( paramValue);
			 * obj = new ObjectInputStream(new ByteArrayInputStream(decoded))
			 * .readObject(); } catch (ClassNotFoundException cnfe) { throw
			 * (SQLException) new SQLException(commonMsg + ", class not found on
			 * controller").initCause(cnfe); } catch (IOException ioe) // like
			 * for instance invalid stream header { throw (SQLException) new
			 * SQLException(commonMsg + ", I/O exception") .initCause(ioe); }
			 * backendPS.setObject(paramIdx, obj); } }
			 */
            else if (paramType.equals(RequestPlayerUtils.REF_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    backendPS.setRef(paramIdx, null);
                } else {
                    throw new SQLException("Ref type not supported");
                }
            } else if (paramType.equals(RequestPlayerUtils.SHORT_TAG)) {
                short t = new Integer(paramValue).shortValue();
                backendPS.setShort(paramIdx, t);
            } else if (paramType.equals(RequestPlayerUtils.STRING_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    backendPS.setString(paramIdx, null);
                } else {
                    backendPS.setString(paramIdx, paramValue);
                }
            } else if (paramType.equals(RequestPlayerUtils.NULL_STRING_TAG)) {
                backendPS.setString(paramIdx, null);
            } else if (paramType.equals(RequestPlayerUtils.TIME_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    backendPS.setTime(paramIdx, null);
                } else {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                        Time t = new Time(sdf.parse(paramValue).getTime());
                        backendPS.setTime(paramIdx, t);
                    } catch (ParseException p) {
                        backendPS.setTime(paramIdx, null);
                        throw new SQLException("Couldn't format time!!!");
                    }
                }
            } else if (paramType.equals(RequestPlayerUtils.TIMESTAMP_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    backendPS.setTimestamp(paramIdx, null);
                } else {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss.S");
                        Timestamp t = new Timestamp(sdf.parse(paramValue)
                                .getTime());
                        backendPS.setTimestamp(paramIdx, t);
                    } catch (ParseException p) {
                        backendPS.setTimestamp(paramIdx, null);
                        throw new SQLException("Couldn't format timestamp!!!");
                    }
                }
            } else if (paramType.equals(RequestPlayerUtils.URL_TAG)) {
                if (paramValue.equals(RequestPlayerUtils.NULL_VALUE)) {
                    backendPS.setURL(paramIdx, null);
                } else {
                    try {
                        backendPS.setURL(paramIdx, new URL(paramValue));
                    } catch (MalformedURLException e) {
                        throw new SQLException("Unable to create URL "
                                + paramValue + " (" + e + ")");
                    }
                }
            } else if (paramType.equals(RequestPlayerUtils.CS_PARAM_TAG)) {
                return true; // ignore, will be treated in the named
                // parameters
            } else {
                return false;
            }

            return true;
        }

        /**
         * Replaces all occurrences of a String within another String. Source:
         * Sequoia's Strings.java
         *
         * @param sourceString source String
         * @param replace      text pattern to replace
         * @param with         replacement text
         * @return the text with any replacements processed, <code>null</code>
         * if null String input
         */
        private String replace(String sourceString, String replace, String with) {
            if (sourceString == null || replace == null || with == null
                    || "".equals(replace)) {
                return sourceString;
            }

            StringBuffer buf = new StringBuffer(sourceString.length());
            int start = 0, end = 0;
            while ((end = sourceString.indexOf(replace, start)) != -1) {
                buf.append(sourceString.substring(start, end)).append(with);
                start = end + replace.length();
            }
            buf.append(sourceString.substring(start));
            return buf.toString();
        }
    }
}
