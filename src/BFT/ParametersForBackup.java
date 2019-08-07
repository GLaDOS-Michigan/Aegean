// $Id: Parameters.java 736 2012-04-06 16:22:29Z manos $

package BFT;

import java.lang.reflect.Field;


public class ParametersForBackup {
    public static boolean useRules = true;
    public static boolean pbRollback = true;
    public static int verifierTimeout = 10000;
    public static long maxRequestSize = 1000000;

    public static int toleratedOrderCrashes = 0;
    public static int toleratedOrderLiars = 0;
    public static int toleratedExecutionCrashes = 0;
    public static int toleratedExecutionLiars = 0;
    public static int toleratedFilterCrashes = 0;
    public static int toleratedFilterLiars = 0;
    public static int toleratedVerifierCrashes = 0;
    public static int toleratedVerifierLiars = 0;
    public static int numberOfClients = 0;
    public static int concurrentRequests = 1;

    public static boolean useVerifier = false;
    public static boolean filtered = false;

    public static boolean fair = false;

    public static boolean debug = false;

    public static boolean filterCaching = true;
    public static boolean doLogging = true;
    public static String execLoggingDirectory = "/tmp";
    public static int batchesPerExecLog = 100;
    public static int execSnapshotInterval = 1000;

    public static String verifierLoggingDirectory = "/test/data";
    public static boolean speculativeForward = false;
    public static boolean chainFilter = false;
    public static boolean chainExecution = false;

    public static boolean insecure = false;
    public static boolean cheapClients = false;

    public static boolean blockingSends = true;

    public static boolean linearizeReads = false;

    public static String provider = "FlexiCore";
    //public static String provider="SunJCE";
    //public static String provider="";
    //    public static int digestLength = 32;
    //    public static String digestType = "SHA-256";
    public static int digestLength = 16;
    public static String digestType = "MD5";
    //public static int digestLength = 4;
    //public static String digestType = "CRC32";

    public static int executionDigestLength = 32;
    public static String executionDigestType = "SHA-256";

    public static int executionPipelineDepth = 1;
    public static int execBatchSize = 40;
    public static int execBatchWaitTime = 20;

    public static int noOfObjects = 2000;
    public static int mergeFactor = 1;
    public static int noOfThreads = 4;
    public static boolean parallelExecution = true;
    public static int falseNegative = -1;
    public static int falsePositive = -1;


    public static int loadBalanceDepth = 3;
    public static boolean useDummyTree = false;
    public static boolean uprightInMemory = false;

    public static boolean level_debug = false;
    public static boolean level_fine = false;
    public static boolean level_info = false;
    public static boolean level_warning = true;
    public static boolean level_error = true;


    public static boolean primaryBackup = false;

    public static String ZKLocation = "";

    public static int getConcurrentRequestLimit() {
        return concurrentRequests;
    }

    public static int getExecutionCount() {
        if (primaryBackup) return 2;
        return toleratedExecutionCrashes
                + max(toleratedExecutionLiars,
                toleratedExecutionCrashes)
                + 1;
    }

    public static int getExecutionLiars() {
        return toleratedExecutionLiars;
    }

    public static int getExecutionCrashes() {
        return toleratedExecutionCrashes;
    }

    public static int getOrderCrashes() {
        return toleratedOrderCrashes;
    }

    public static int getOrderLiars() {
        return toleratedOrderLiars;
    }

    public static int getOrderCount() {
        if (useVerifier || primaryBackup)
            return 0;
        return 2 * toleratedOrderCrashes + toleratedOrderLiars + 1;
    }

    public static int getNumberOfClients() {
        return numberOfClients;
    }

    public static int getFilterLiars() {
        return toleratedFilterLiars;
    }

    public static int getFilterCrashes() {
        return toleratedFilterCrashes;
    }

    public static int getFilterCount() {
        //	int v1 = 3*getFilterLiars() + getFilterCrashes()+1;
        //      int v2 = 0; //2*getFilterLiars() + 2*getFilterCrashes()+1;
        if (primaryBackup) return 0;
        return
                filtered ? (fair ? (1 + getFilterCrashes() +
                        2 * max(getFilterCrashes(), getFilterLiars()) +
                        getFilterLiars())
// 		      :(1+min(getFilterCrashes(), getFilterLiars()) +  
// 			2*max(getFilterCrashes(), getFilterLiars())))
                        : (1 + max(getFilterCrashes(), getFilterLiars()) +
                        getFilterLiars() + getFilterCrashes()))
                        : 0;

// 	return filtered?(fair?(3*max(getFilterCrashes(), getFilterLiars())+
// 			       min(getFilterLiars(), getFilterCrashes()))
// 			 :2*max(getFilterCrashes(), getFilterLiars())+
// 			 getFilterLiars()):0;
        //	return filtered?((v1>v2)?v1:v2):0;
    }

    public static int getVerifierLiars() {
        return toleratedVerifierLiars;
    }

    public static int getVerifierCrashes() {
        return toleratedVerifierCrashes;
    }

    public static int getVerifierCount() {
        if (!useVerifier || primaryBackup)
            return 0;
        //FIXME here
        return 2 * toleratedVerifierCrashes + toleratedVerifierLiars + 1;
    }

    public static int max(int a, int b) {
        if (a > b)
            return a;
        else
            return b;
    }

    public static int min(int a, int b) {
        if (a < b)
            return a;
        else
            return b;
    }

    // quorum sizes for the execution
    public final static int rightExecutionQuorumSize() {
        return getExecutionLiars() + 1;
    }

    public final static int upExecutionQuorumSize() {
        return getExecutionCrashes() + 1;
    }

    public final static int smallExecutionQuorumSize() {
        return max(rightExecutionQuorumSize(), upExecutionQuorumSize());
    }

    public final static int linearizedExecutionQuorumSize() {
        //	BFT.Debug.kill("I DONT THINK THIS QUORUM SIZE WORKS PROPERLY");
        return getExecutionCount() - getExecutionLiars();
    }

    public final static int largeExecutionQuorumSize() {
        return getExecutionCount() - getExecutionCrashes();
    }

    // quorum sizes for the orders
    public final static int smallOrderQuorumSize() {
        //return (getOrderCount() - getOrderCrashes())/2+1;
        return getOrderLiars() + 1;
        //return (getOrderCount() + getOrderLiars())/2+1;
    }

    public final static int largeOrderQuorumSize() {
        return getOrderCount() - getOrderCrashes();
    }

    public final static int fastOrderQuorumSize() {
        return getOrderCount();
    }

    // quorum sizes for the verifiers
    public final static int smallVerifierQuorumSize() {
        return getVerifierLiars() + 1;
    }

    public final static int largeVerifierQuorumSize() {
        return getVerifierCount() - getVerifierCrashes();
    }

    // quorum sizes for the filters
    public final static int smallFilterQuorumSize() {
        if (getFilterLiars() > getFilterCrashes())
            return getFilterLiars() + 1;
        else
            return getFilterCrashes() + 1;
    }

    public final static int mediumFilterQuorumSize() {
        return smallFilterQuorumSize() + getFilterLiars();
    }

    public final static int largeFilterQuorumSize() {
        return mediumFilterQuorumSize() + getFilterCrashes();
    }

    public boolean checkId(String role, int id) {
        int maxId = 0;
        if (role.equalsIgnoreCase("exec")) {
            maxId = getExecutionCount() - 1;
        } else if (role.equalsIgnoreCase("order")) {
            maxId = getOrderCount() - 1;
        } else if (role.equalsIgnoreCase("client")) {
            maxId = getNumberOfClients() - 1;
        } else if (role.equalsIgnoreCase("verifier")) {
            maxId = getVerifierCount() - 1;
        }
        return (id >= 0 && id <= maxId);
    }

    public static void println(Object obj) {
        if (debug) {
            System.out.println(obj);
        }
    }

    public static void print(String str) {
        if (debug) {
            System.out.print(str);
        }
    }

    public static void copy() {
        try {
            Class cls = BFT.ParametersForBackup.class;
            Class cls2 = BFT.Parameters.class;
            Field[] fields = cls.getDeclaredFields();
            Field[] fields2 = cls2.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getType() == int.class) {
                    fields[i].setInt(null, fields2[i].getInt(null));
                } else if (fields[i].getType() == long.class) {
                    fields[i].setLong(null, fields2[i].getLong(null));
                } else if (fields[i].getType() == byte.class) {
                    fields[i].setByte(null, fields2[i].getByte(null));
                } else if (fields[i].getType() == char.class) {
                    fields[i].setChar(null, fields2[i].getChar(null));
                } else if (fields[i].getType() == short.class) {
                    fields[i].setShort(null, fields2[i].getShort(null));
                } else if (fields[i].getType() == boolean.class) {
                    fields[i].setBoolean(null, fields2[i].getBoolean(null));
                } else if (fields[i].getType() == float.class) {
                    fields[i].setFloat(null, fields2[i].getFloat(null));
                } else if (fields[i].getType() == double.class) {
                    fields[i].setDouble(null, fields2[i].getDouble(null));
                } else if (fields[i].getType() == String.class) {
                    fields[i].set(null, (String) fields2[i].get(null));
                } else {
                    System.out.println("unrecongnized type!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
