package BFT;


public class Parameters {
    //Parameters to use for batch suggestion remzi
    transient public int newNestedGroupNo = 0;
    transient public int numberOfNestedRequestInTheSlice = 0;
    transient public boolean batchSuggestion = false;
    transient public boolean normalMode = false;


    //!!! readConfiguration method of the Membership requires property and the fields should have the exactly same name
    public boolean useRules = true;
    public boolean pbRollback = true; //TODO field to indicate rollback??
    public int verifierTimeout = 10000;
    public long maxRequestSize = 1000000; //maximum request of the size of a single request in bytes. If operation(byte
                                          //array).length > maxRequestSize, this is not valid

    public int toleratedOrderCrashes = 0;
    public int toleratedOrderLiars = 0;
    public int toleratedExecutionCrashes = 0; //(u) getExecutionCount() uses this to calculate the number of execution replicas
                                              //u + max(u,r) + 1. This variable can be changed in readConfiguration function
                                              //of the BFT.membership.Membership class. (Its constructor uses this function)
    public int toleratedExecutionLiars = 0;   //(r) Similar to toleratedExecutionCrashes.
    public int toleratedFilterCrashes = 0;    //These two variable related to filter replica numbers, also 'filtered'
    public int toleratedFilterLiars = 0;      //variable is used. Membership generator asks for need for the filters first
    public int toleratedVerifierCrashes = 0;  //(u) for Verifiers. Verifier version of toleratedExecutionCrashes
    public int toleratedVerifierLiars = 0;    //(r) for Verifiers. Similar to toleratedVerifierCrashes. 2u+r+1
    public int numberOfClients = 16;          //startClient.py changes numberOfClients property according to startClient
                                              //endClient arguments of the modified_super.py. And numberOfClients propery will
                                              //change this field in readConfiguration but may it be changed later on??
                                              //There are lots of usage, I should check how it runs in normal run and take
                                              //notes here
    public int concurrentRequests = 10;       //Currently modified_super.py changes this parameter to 192. no usage

    public boolean useVerifier = false;       //Currently modified_super.py changes this to true but normally it becames
                                              //false in Primary Backup setting. As far as I understand, if this is false
                                              //orders are used. Also, config.sh defines this value when it asks "Choose
                                              // Mode: YES(default) for exec-verfier and NO for order-exec:"
    public boolean filtered = false;

    public boolean fair = false;              //It is related to filters but there is nothing which modifies it.

    public boolean debug = false;             //TODO It seems this parameter is useless, I can remove it. Try to compile afterwards
                                              //Also, it is confusing with modified_super.py argument
    public boolean filterCaching = false;     //Currently, modified_super.py makes this false, it is something related to
                                              //caching in filter nodes
    public boolean doLogging = true;          //currently, modified_super.py makes this false. It is related to logger usage
    public String execLoggingDirectory = "/tmp"; //it is /tmp everywhere(here,modified_super.py and properties)
    public int batchesPerExecLog = 100;
    public int execSnapshotInterval = 1000;

    public String verifierLoggingDirectory = "/test/data";
    public boolean speculativeForward = false; //THere is nothing which changes this. TODO I should remove maybe
    public boolean chainFilter = false;        //""". Disregard its usage for now
    public boolean chainExecution = false;     //"""  """

    public boolean insecure = false; //if toleratedFilterLiars and toleratedOrderLiars is zero, this becomes true. Authentication
                                     //ile ilgili bir sey, MAC le filan kullaniliyor genelde
    public boolean cheapClients = false; //THere is nothing which changes this.  Disregard its usage for now

    public boolean blockingSends = true;  //THere is nothing which changes this. THere is no usage as well. TODO I should remove maybe

    public boolean linearizeReads = false; //THere is nothing which changes this. TODO I should remove maybe

    public String provider = "FlexiCore";  //THere is nothing which changes this. It is used to create keys but I didn't
                                           //understand why it has this deault value
    public int digestLength = 16;          //This is determined in Membership constructor according to digestType.
    public String digestType = "MD5";      //MD5 creates 16 bytes hash, sha-256 creates 32 bytes, CRC32 creates 8 bytes.
                                           //Modified_super makes digestType sha-256

    public int executionDigestLength = 32; //SimilarToDigest length but just used in BenchDigest TODO maybe useless. In the
                                           //part which changes this length CRC32 makes it 4. I think it is inconsistent with
                                           //digestType. TODO I think digestType also should be 4
    public String executionDigestType = "SHA-256";

    // The amount we will stop waiting at and always release the batch
    public int execBatchSize = 40;
    // The amount we need before releasing the batch (unless we fully timeout)
    public int execBatchMinSize = 20; //It seems this is not used in the code. It is assigned to another variable in CreateBatchThread
                                      //but this variable not used. TODO remove?
    public int execBatchWaitTime = 20;
    // Time to wait before polling the queue in microseconds
    public int execBatchPollTime = 1000;
    // The amount of time to wait between hitting the min to release the batch
    public int execBatchWaitFillTime = 10;
    public int execBatchNoChangeWindow = 5;
    public boolean dynamicBatchFillTime = false;
    public int replyCacheSize = 10;


    public boolean pipelinedSequentialExecution = false; //THe next 3 variables are first set to false by modified_super.py
                                                         //and then they are changed according to --mode fore middle service.
                                                         //(s) sequential leaves all false
                                                         //(sp) pipelined sequential makes pipelinedSequentialExecution true
                                                         //(p) parallel makes parallelExecution true
                                                         //(pp) parallel pipelined makes parallelExecution and pipelinedBatchExecution true.
                                                         //TODO tracing these three variables can give a good idea about modes of running
    public boolean pipelinedBatchExecution = false;
    public boolean parallelExecution = true;
    public int numPipelinedBatches = 1; //It is num_batches as modified_super.py argument and its explanation is
                                        // 'Number of groups executed concurrently for parallel or sequential pipelined
                                        // executions'. modified_super makes it 2 in default. TODO
    public int numWorkersPerBatch = 4;  //It is num_threads as modified_super.py argument and its explanation is 'Number
                                        // of threads that executed client requests concurrently. There are this many
                                        // threads working during each batch if the run is pipelined'. (default num_thread = 8)
    public int noOfThreads = 4;         // It is also num_threads as modified_super.py argument. It directly changes
                                        //noOfThreads for middle service but for backend noOfThreads set to min(num_threads,
                                        // 16).
    public int executionPipelineDepth = 1; //modified_super makes it 10.
    public int sliceExecutionPipelineDepth = 2;  //modified_super.py sets it to 2


    public boolean rollbackDisabled = true;
    public boolean sendVerify = true;
    public boolean backendAidedVerification = true;
    public boolean sendNested = true;
    public boolean randomNestedRequest = false;
    public boolean extraNestedRequest = false;
    // This will force certain execution nodes to delete their digest, creating a divergence and state transfer
    public boolean causeDivergence = false;
    // Forces the verifiers to trigger a view change and rollback on the 1000th seqNo
    public boolean forceRollback = false;
    // Forces the system to execute sequentially after the 500th request
    public boolean forceSequential = false;
    public boolean forceExecutionRollback = false;//I have added to test rollback-Remzi

    public boolean CFTmode = false;
    public int noOfObjects = 1000; //modified_super.py set it to 100000, It is used in MerkleTree initialization. It may be
                                   //number of objects in the merkle tree. But should it be predefined? Modified_super.py
                                   //sets it both for middle and backend. MembershipGenerator makes this 1000 for backend
                                   // and 1M for middle in default. And it says "Middle service needs all stack trace
                                   // being put in MT" which explains this parameter's usage a bit more. However, both is
                                   // overwriten by modified_super anyway. TODO
    public int mergeFactor = 1;     //nothing changes this parameter. Something related to merkle tree.
    public int falseNegative = -1;  //THere is nothing changes this variable and BFT.exec.glue.GeneralGlue use it only once.
    public int falsePositive = -1;  //"" "" ""
    public int threadPoolSize = 1; //modified_super makes it 1. TODO Applications.echo.EchoServer use it but I don't understand
                                   //what is EchoServer and why it uses it.

    //public double backendLoopRatio = 1.0; //It seems only used via Membership.backendLoopRatio and it is only used to
                                          //create BenchmarkRequest. Modified_super makes it 1.0. It may refer to the numbers
                                          // which are used first graph in the thesis. TODO I think it is related to
                                          //how much the backend will wait to respond but I didn't understand exactly
    public boolean fillBatchesFirst = true;

    public int loadBalanceDepth = 3;
    public boolean useDummyTree = true; //what is this parameter for? It is default is false except here. TODO I should
                                        // trace how it is used in merkle tree.
                                        //Maybe it just sends a fixed hash which is the same for all verifiers. 
    public boolean uprightInMemory = false; //TODO trace to understand what is it for? Its default is true except here

    // Adam Pipeline test flags
    public boolean clientReadOnly = false;
    public boolean clientNoConflict = false; //modified_super makes this false.TODO I didn't understand its usage
    public boolean roundBatchSize = true; //There is nothing to change this. It is also used in the same place below related
                                          //to batching
    public boolean runMixerAfter = false; //THere is nothing to change this. It is used in ExecBaseNode.CreateBatchThread
                                          //(extends thread and uses the parameters related to batching like execBatchSize.
                                          //runMixedAfter is used in run. TODO isn't mixer running?

    public static int checkPointInterval = 100; //There is nothing to change this. It seems related to checkpoint and GC.
    public static boolean level_debug = false;
    public static boolean level_fine = false;
    public static boolean level_info = false;
    public static boolean level_warning = false;
    public static boolean level_error = true;
    public static boolean backendSpeculation = false;

    public boolean primaryBackup = false;
    public boolean primaryBackupClient = false;
    public boolean isMiddleClient = false;

    public String ZKLocation = "";

    public static String instrumentationClassListFile = "toInstrument.txt";
    public boolean level_trace = false;
    public boolean unreplicated = false;

    public int getConcurrentRequestLimit() {
        return concurrentRequests;
    }

    public int getExecutionCount() {
        if (primaryBackup) return 2;
        return toleratedExecutionCrashes
                + max(toleratedExecutionLiars,
                toleratedExecutionCrashes)
                + 1;
    }

    public int getExecutionLiars() {
        return toleratedExecutionLiars;
    }

    public int getExecutionCrashes() {
        return toleratedExecutionCrashes;
    }

    public int getOrderCrashes() {
        return toleratedOrderCrashes;
    }

    public int getOrderLiars() {
        return toleratedOrderLiars;
    }

    public int getOrderCount() {
        if (useVerifier || primaryBackup)
            return 0;
        return 2 * toleratedOrderCrashes + toleratedOrderLiars + 1;
    }

    public int getNumberOfClients() {
        return numberOfClients;
    }

    public int getFilterLiars() {
        return toleratedFilterLiars;
    }

    public int getFilterCrashes() {
        return toleratedFilterCrashes;
    }

    public int getFilterCount() {
        if (primaryBackup) {
            return 0;
        }

        return filtered ? (fair ? (1 + getFilterCrashes() +
                2 * max(getFilterCrashes(), getFilterLiars()) +
                getFilterLiars())
                : (1 + max(getFilterCrashes(), getFilterLiars()) +
                getFilterLiars() + getFilterCrashes()))
                : 0;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getVerifierLiars() {
        return toleratedVerifierLiars;
    }

    public int getVerifierCrashes() {
        return toleratedVerifierCrashes;
    }

    public int getVerifierCount() {
        if (!useVerifier || primaryBackup) {
            return 0;
        }
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
    public final int rightExecutionQuorumSize() {
        //In agree execute mode the normal client response is speculative so it waits same number of replica as the backend
        //shim layer
        if(!normalMode && !isMiddleClient & !useVerifier) {
            return getExecutionLiars() + getExecutionCrashes() + 1;
        }
        return getExecutionLiars() + 1;
    }

    public final int upExecutionQuorumSize() {
        return getExecutionCrashes() + 1;
    }

    public final int smallExecutionQuorumSize() {
        return max(rightExecutionQuorumSize(), upExecutionQuorumSize());
    }

    public final int linearizedExecutionQuorumSize() {
        //	BFT.Debug.kill("I DONT THINK THIS QUORUM SIZE WORKS PROPERLY");
        return getExecutionCount() - getExecutionLiars();
    }

    public final int largeExecutionQuorumSize() {
        return getExecutionCount() - getExecutionCrashes();
    }

    // quorum sizes for the orders
    public final int smallOrderQuorumSize() {
        //return (getOrderCount() - getOrderCrashes())/2+1;
        return getOrderLiars() + 1;
        //return (getOrderCount() + getOrderLiars())/2+1;
    }

    public final int largeOrderQuorumSize() {
        return getOrderCount() - getOrderCrashes();
    }

    public final int fastOrderQuorumSize() {
        return getOrderCount();
    }

    // quorum sizes for the verifiersType a message...
    public final int smallVerifierQuorumSize() {
        return getVerifierLiars() + 1;
    }

    public final int largeVerifierQuorumSize() {
        return getVerifierCount() - getVerifierCrashes();
    }

    // quorum sizes for the filters
    public final int smallFilterQuorumSize() {
        if (getFilterLiars() > getFilterCrashes())
            return getFilterLiars() + 1;
        else
            return getFilterCrashes() + 1;
    }

    public final int mediumFilterQuorumSize() {
        return smallFilterQuorumSize() + getFilterLiars();
    }

    public final int largeFilterQuorumSize() {
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

    public void println(Object obj) {
        if (debug) {
            System.out.println(obj);
        }
    }

    public void print(String str) {
        if (debug) {
            System.out.print(str);
        }
    }

}
