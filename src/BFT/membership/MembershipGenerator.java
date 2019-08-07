/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BFT.membership;

import BFT.Parameters;
import BFT.util.KeyGen;
import BFT.util.KeyStringPair;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author yangwang
 */
public class MembershipGenerator {

    private final static Map<String, Integer> startPortMap = new HashMap<String, Integer>();
    private static int defaultStartPort = 8500;
    private static final String SHA1 = "HmacSHA1";
    private static final String MD5 = "HmacMD5";
    private static final String mactype = SHA1;
    private static HashMap<String, Integer> ports = new HashMap<String, Integer>();
    private Parameters parameters;

    public static void main(String[] args) throws Exception {
        boolean isBackend = false;
        boolean primaryBackup = false;
        boolean useVerifier = true;
        boolean filtered = true;
        int toleratedOrderLiars = 0;
        int toleratedOrderCrashes = 0;
        int orderCount = 0;

        int toleratedExecutionLiars = 0;
        int toleratedExecutionCrashes = 0;
        int execCount = 0;

        int toleratedFilterLiars = 0;
        int toleratedFilterCrashes = 0;
        int filterCount = 0;

        int toleratedVerifierLiars = 0;
        int toleratedVerifierCrashes = 0;
        int verifierCount = 0;

        int serverCount = 0;
        int clientCount = 0;

        int noOfThreads = 0;
        boolean parallelExecution = true;
        int threadPoolSize = 1;

        primaryBackup = getValue("Primary Backup: YES or No (default): ", false);

        if (primaryBackup) {
            filtered = false;
            useVerifier = false;
        }

        isBackend = getValue("Is this for backend service? YES or NO (default), if you are not clear about what this means, just type NO and everything will be the same as before. ", false);
        if (isBackend) {
            System.err.println("Attention: You are now configuring backend service, \"Clients\" are actually ClientShims for sending nested request.");
            defaultStartPort = getValue("start port for the config: ", 9000);
            System.err.println("The default start port is set to " + defaultStartPort + ", you can still configure it from your machine list files.");
        }

        if(!primaryBackup)
        {
            useVerifier = getValue("Choose Mode: YES(default) for exec-verfier and NO for order-exec: ", true);
            filtered = getValue("Do you need filter node: YES(Default) or NO: ", true);

            if (!useVerifier) {
                toleratedOrderLiars = getValue("How many order liar failures (Default 0)? ", 0);
                toleratedOrderCrashes = getValue("How many order crash failures (Default 0)? ", 0);
            } else {
                toleratedVerifierLiars = getValue("How many verifier liar failures (Default 0)? ", 0);
                toleratedVerifierCrashes = getValue("How many verifier crash failures (Default 0)? ", 0);
            }

            toleratedExecutionLiars = getValue("How many exec liar failures (Default 0)? ", 0);
            toleratedExecutionCrashes = getValue("How many exec crash failures (Default 0)? ", 0);
        }

        if (filtered) {
                toleratedFilterLiars = getValue("How many filter liar failures (Default 0)? ", 0);
                toleratedFilterCrashes = getValue("How many filter crash failures (Default 0)? ", 0);
        }


        clientCount = getValue("How many clients (Default 1)? ", 1);

        int[] replicatedClientCrash = new int[clientCount];
        int[] replicatedClientLiar = new int[clientCount];
        int[] replicatedClientCount = new int[clientCount];

        int clientCrash = 0; //u for clients. Clients are the execs of the middle, in case of the backend
        int clientLiar = 0; //r for clients.

        boolean primaryBackupClient = false;
        if(isBackend)
        {
            primaryBackupClient = getValue("Is client running in primary-backup mode: YES or No (default): ", false);
        }
        
        if(!primaryBackupClient) {
                clientCrash = getValue("How many client crash failures (Default 0) for Clients of backend? It should"
                        + " be the same with execution crash failures of middle service? ", 0);
                clientLiar = getValue("How many client liar failures (Default 0) for Clients of backend?  It should"
                        + " be the same with execution liar failures of middle service? ", 0);
        }

        for (int i = 0; i < clientCount; i++) {
            if(primaryBackupClient) {
                replicatedClientCount[i] = 2;
            }
            else {
                replicatedClientCrash[i] = clientCrash;
                replicatedClientLiar[i] = clientLiar;
                replicatedClientCount[i] = clientCrash + Parameters.max(clientCrash, clientLiar) + 1;
            }
        }

        noOfThreads = getValue("How many threads (noOfThreads) (Default 4)? ", 4);

        threadPoolSize = getValue("How many threads (threadPoolSize) for processing batch(Default 1)?", 1);

        Parameters param = new Parameters(); //TODO use the same names for the same parameters
        param.primaryBackup = primaryBackup;
        param.useVerifier = useVerifier;
        param.filtered = filtered;
        param.toleratedOrderLiars = toleratedOrderLiars;
        param.toleratedOrderCrashes = toleratedOrderCrashes;
        param.toleratedExecutionLiars = toleratedExecutionLiars;
        param.toleratedExecutionCrashes = toleratedExecutionCrashes;
        param.toleratedVerifierLiars = toleratedVerifierLiars;
        param.toleratedVerifierCrashes = toleratedVerifierCrashes;
        param.toleratedFilterLiars = toleratedFilterLiars;
        param.toleratedFilterCrashes = toleratedFilterCrashes;
        orderCount = param.getOrderCount();
        execCount = param.getExecutionCount();
        verifierCount = param.getVerifierCount();
        filterCount = param.getFilterCount();
        parallelExecution = useVerifier ? getValue("Parallel Execution YES(Default) or NO? ", true) : false;


        serverCount = orderCount + execCount + verifierCount + filterCount;

        String suf = "";
        if (isBackend)
            suf = ".backend";
        LinkedList<String> orderList = null;
        if (orderCount > 0) {
            orderList = readServerList(getFileName(String.format("The order list file (Default ./orders%s)? ", suf), "./orders" + suf), orderCount);
        }

        LinkedList<String> execList = null;
        if (execCount > 0) {
            execList = readServerList(getFileName(String.format("The exec list file (Default ./execs%s)? ", suf), "./execs" + suf), execCount);
            System.out.println("Exec list: " + execList);
        }

        LinkedList<String> verifierList = null;
        if (verifierCount > 0) {
            verifierList = readServerList(getFileName(String.format("The verifier list file (Default ./verifiers%s)? ", suf), "./verifiers" + suf), verifierCount);
            System.out.println("Verifier list: " + verifierList);
        }
        LinkedList<String> filterList = null;
        if (filterCount > 0) {
            filterList = readServerList(getFileName(String.format("The filter list file (Default ./filters%s)? ", suf), "./filters" + suf), filterCount);
            System.out.println("Filter list: " + filterList);
        }

        LinkedList<String> clientList = null;
        if (clientCount > 0) { //TODO important place to fix. While using config.sh it prints three local host in middle, one in backend. When u and r is 1, other lists are printed 3 times.
            if (isBackend) {
                clientList = readServerList(getFileName(String.format("The client list file (Default ./clients%s)", suf), "./clients" + suf), replicatedClientCount[0]);
                System.out.println("Client list: " + clientList);
            } else {
                clientList = readServerList(getFileName(String.format("The client list file (Default ./clients%s)", suf), "./clients" + suf), clientCount);
                System.out.println("Client list: " + clientList);
            }
        }
        String answer;

        if(useVerifier) {
            answer = getValue(String.format("The output file Default ./test.properties%s? ", suf), "./test.properties" + suf);
        }
        else if(primaryBackup){
            answer = getValue(String.format("The output file Default ./testPB.properties%s? ", suf), "./testPB.properties" + suf);
        }
        else
        {
            answer = getValue(String.format("The output file Default ./testSeq.properties%s? ", suf), "./testSeq.properties" + suf);
        }

        File file = new File("keys");
        if (!file.exists()) {
            file.mkdir();
        }
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(answer)));

        out.write("primaryBackup=" + primaryBackup);
        out.newLine();

        out.write("useVerifier=" + useVerifier);
        out.newLine();
        out.write("filtered=" + filtered);
        out.newLine();
        if (orderCount > 0) {

            out.write("toleratedOrderCrashes = " + toleratedOrderCrashes);
            out.newLine();
            out.write("toleratedOrderLiars = " + toleratedOrderLiars);
            out.newLine();
        }
        if (execCount > 0) {
            out.write("toleratedExecutionCrashes = " + toleratedExecutionCrashes);
            out.newLine();
            out.write("toleratedExecutionLiars = " + toleratedExecutionLiars);
            out.newLine();
        }
        if (verifierCount > 0) {
            out.write("toleratedVerifierCrashes = " + toleratedVerifierCrashes);
            out.newLine();
            out.write("toleratedVerifierLiars = " + toleratedVerifierLiars);
            out.newLine();
        }
        if (filterCount > 0) {
            out.write("toleratedFilterCrashes = " + toleratedFilterCrashes);
            out.newLine();
            out.write("toleratedFilterLiars = " + toleratedFilterLiars);
            out.newLine();
        }
        out.write("numberOfClients = " + clientCount);
        out.newLine();
        out.write("filterCaching = false");
        out.newLine();
        out.write("threadPoolSize = " + threadPoolSize);
        out.newLine();
        out.write("noOfThreads = " + noOfThreads);
        out.newLine();
        out.write("parallelExecution = " + parallelExecution);
        out.newLine();

        out.write("doLogging = false");
        out.newLine();

        out.write("execLoggingDirectory = /tmp");
        out.newLine();

        out.write("execSnapshotInterval = 1000");
        out.newLine();

        out.write("verifierLoggingDirectory = /test/data");//TODO what is this variable?
        out.newLine();

        out.write("insecure = false");//TODO shouldn't it be extracted from u and r?
        out.newLine();

        out.write("digestType = SHA-256");
        out.newLine();

        out.write("executionPipelineDepth = 10");
        out.newLine();

        out.write("execBatchSize = 40");
        out.newLine();

        out.write("execBatchWaitTime = 20");
        out.newLine();

        out.write("loadBalanceDepth = 2");
        out.newLine();

        out.write("useDummyTree = false");
        out.newLine();

        out.write("uprightInMemory = true");
        out.newLine();
        out.write("noOfObjects = 100000");


        out.newLine();

        out.write("sliceExecutionPipelineDepth = 1");
        out.newLine();
        out.write("replyCacheSize = 1");
        out.newLine();

        out.write("level_debug = false");
        out.newLine();
        out.write("level_fine = false");
        out.newLine();
        out.write("level_info = false");
        out.newLine();
        out.write("level_warning = true");
        out.newLine();
        out.write("level_error = true");
        out.newLine();
        out.write("level_trace = false");
        out.newLine();

        // Jim and David's additions to the file?

        out.write("pipelinedBatchExecution = false");
        out.newLine();
        out.write("pipelinedSequentialExecution = false");
        out.newLine();
        out.write("numPipelinedBatches = 1");
        out.newLine();
        out.write("numWorkersPerBatch = 1");
        out.newLine();
        out.write("rollbackDisabled = true");
        out.newLine();
        out.write("fillBatchesFirst = true");
        out.newLine();
        out.write("sendVerify = true");
        out.newLine();
	    out.write("sendNested = true");
        out.newLine();
        out.write("batchSuggestion = true");
        out.newLine();
        out.write("concurrentRequests = 192");
        out.newLine();
        out.newLine();
        out.write("clientReadOnly= false");
        out.newLine();
        out.write("clientNoConflict = false");
        out.newLine();
        out.write("execBatchNoChangeWindow = 2");
        out.newLine();
        out.write("extraNestedRequest = false");
        out.newLine();
        out.write("randomNestedRequest = false");
        out.newLine();
        out.write("backendAidedVerification = " + !isBackend);
        out.newLine();
        out.write("isMiddleClient = " + isBackend);
        out.newLine();
        out.write("primaryBackupClient = " + primaryBackupClient);
        out.newLine();
        out.write("normalMode = false");
        out.newLine();
        out.write("unreplicated = " + isBackend);
        out.newLine();

        if (orderCount > 0) { //TODO another important place to understrand. How serverCount + 1 is related to offset and how the ports are given and used
            for (int i = 0; i < orderList.size(); i++) {
                generateOneServer(out, "ORDER", i, orderList.get(i), serverCount + 1);
            }
        }
        out.newLine();
        if (execCount > 0) {
            for (int i = 0; i < execList.size(); i++) {
                generateOneServer(out, "EXEC", i, execList.get(i), serverCount + 1);
            }
        }
        out.newLine();
        if (verifierCount > 0) {
            for (int i = 0; i < verifierList.size(); i++) {
                generateOneServer(out, "VERIFIER", i, verifierList.get(i), serverCount + 1);
            }
        }
        out.newLine();
        if (filterCount > 0) {
            for (int i = 0; i < filterList.size(); i++) {
                generateOneServer(out, "FILTER", i, filterList.get(i), serverCount + 1);
            }
        }
        out.newLine();
        int noOfIter = clientList.size();

        if (isBackend)
            noOfIter = clientCount;
        if (clientCount > 0) {
            for (int i = 0; i < noOfIter; i++) {
                for (int j = 0; j < replicatedClientCount[i]; j++) {
                    if (isBackend) {
                        generateReplicatedClient(out, i, j, clientList.get(j % (clientList.size())), serverCount + 1);
                    } else {
                        generateReplicatedClient(out, i, j, clientList.get(i), serverCount + 1);
                    }
                }
                StringBuilder builder = new StringBuilder();
                builder.append("CLIENT").append(".").append(i).append(".").append("toleratedCrashes").append(" = ").append(replicatedClientCrash[i]);
                out.write(builder.toString());
                out.newLine();
                StringBuilder builder2 = new StringBuilder();
                builder2.append("CLIENT").append(".").append(i).append(".").append("toleratedLiars").append(" = ").append(replicatedClientLiar[i]);
                out.write(builder2.toString());
                out.newLine();
                //generateOneServer(out, "CLIENT", i, clientList.get(i), serverCount+1);
            }
        } //TODO it is important until here
        out.newLine();
        out.flush();
    }

    private static void replaceStartPort(String hostname, int startPort) {
        if (startPortMap.containsKey(hostname)) {
            int oldPort = startPortMap.get(hostname);
            if (oldPort < startPort) {
                startPortMap.put(hostname, startPort);
            }
        } else {
            startPortMap.put(hostname, startPort);
        }
    }

    private static LinkedList<String> readServerList(String fileName, int count) throws IOException {
        LinkedList<String> ret = new LinkedList<String>();
        System.out.println(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
        for (int i = 0; i < count; i++) {
            String line = reader.readLine();
            String hostname = null;
            int portStart = defaultStartPort;
            if (line == null || line.equals(""))
                break;
            if (line.indexOf(":") != -1) {
                String[] hostNPortStart = line.split(":");
                assert (hostNPortStart.length == 2);

                hostname = hostNPortStart[0].trim();
                portStart = Integer.parseInt(hostNPortStart[1].trim());
            } else {
                hostname = line.trim();
            }
            replaceStartPort(hostname, portStart);
            ret.add(hostname);
        }
        int k = ret.size();
        for (int i = k; i < count; i++)
            ret.add(ret.get(i - k));
        return ret;
    }

    private static int getValue(String message, int defaultValue) {
        while (true) {
            try {
                System.out.print(message);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (answer.equals(""))
                    return defaultValue;
                else
                    return Integer.parseInt(answer);
            } catch (Exception e) {
                System.out.println("Unexpected input");
            }
        }
    }

    private static String getFileName(String message, String defaultValue) {
        while (true) {
            try {
                System.out.print(message);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (answer.equals(""))
                    answer = defaultValue;
                File f = new File(answer);
                if (f.exists())
                    return answer;
                else
                    System.out.println("File not found");
            } catch (Exception e) {
                System.out.println("Unexpected input");
            }
        }
    }

    private static boolean getValue(String message, boolean defaultValue) {
        while (true) {
            try {
                System.out.print(message);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (answer.equals(""))
                    return defaultValue;
                else {
                    if (answer.equalsIgnoreCase("Yes") || answer.equalsIgnoreCase("Y"))
                        return true;
                    else if (answer.equalsIgnoreCase("No") || answer.equalsIgnoreCase("N"))
                        return false;
                    else
                        System.out.println("Unexpected input");
                }
            } catch (Exception e) {
                System.out.println("Unexpected input");
            }
        }
    }

    private static String getValue(String message, String defaultValue) {
        while (true) {
            try {
                System.out.print(message);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (answer.equals(""))
                    return defaultValue;
                else {
                    return answer;
                }
            } catch (Exception e) {
                System.out.println("Unexpected input");
            }
        }
    }

    private static void generateOneServer(BufferedWriter out, String role, int index, String host, int portNum) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append(role).append(".").append(index).append(" = ");
        BufferedWriter keyOut = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream("keys/" + role + index + ".privk.properties")));
        if (!ports.containsKey(host)) {
            assert (startPortMap.containsKey(host));
            ports.put(host, startPortMap.get(host));
        }
        for (int i = 0; i < portNum; i++) {
            int port = ports.get(host);
            builder.append(host).append(":").append(port).append(" ");
            ports.put(host, port + 1);
        }
        KeyGen kg = new KeyGen("FlexiCore");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.generateKeyPair();
        KeyStringPair kpStrings = kg.keysToString(keyPair);
        keyOut.write("PUB = " + kpStrings.getPubString());
        keyOut.newLine();
        builder.append(kpStrings.getPubString());
        keyOut.write("PRIV = " + kpStrings.getPrivString());
        keyOut.newLine();

        KeyGenerator keyGen2 = KeyGenerator.getInstance(mactype);
        SecretKey sk = keyGen2.generateKey();
        BigInteger keyInt = new BigInteger(sk.getEncoded());
        //String keyString = keyInt.toString(16).toUpperCase();
        String keyString = "12345678901234567890123456789012345678901234567890" +
                "123456789012345678901234567890123456789012345678901234567890" +
                "123456789012345678";
        keyOut.write("SECRET = " + keyString);
        keyOut.newLine();
        keyOut.flush();
        out.write(builder.toString());
        out.newLine();
    }

    private static void generateReplicatedClient(BufferedWriter out, int index, int subId, String host, int portNum) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("CLIENT").append(".").append(index).append(".").append(subId).append(" = ");
        BufferedWriter keyOut = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream("keys/CLIENT" + index + "." + subId + ".privk.properties")));
        if (!ports.containsKey(host)) {
            assert (startPortMap.containsKey(host));
            ports.put(host, startPortMap.get(host));
        }
        for (int i = 0; i < portNum; i++) {
            int port = ports.get(host);
            builder.append(host).append(":").append(port).append(" ");
            ports.put(host, port + 1);
        }
        KeyGen kg = new KeyGen("FlexiCore");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.generateKeyPair();
        KeyStringPair kpStrings = kg.keysToString(keyPair);
        keyOut.write("PUB = " + kpStrings.getPubString());
        keyOut.newLine();
        builder.append(kpStrings.getPubString());
        keyOut.write("PRIV = " + kpStrings.getPrivString());
        keyOut.newLine();

        KeyGenerator keyGen2 = KeyGenerator.getInstance(mactype);
        SecretKey sk = keyGen2.generateKey();
        BigInteger keyInt = new BigInteger(sk.getEncoded());
        //String keyString = keyInt.toString(16).toUpperCase();
        String keyString = "12345678901234567890123456789012345678901234567890" +
                "123456789012345678901234567890123456789012345678901234567890" +
                "123456789012345678";
        keyOut.write("SECRET = " + keyString);
        keyOut.newLine();
        keyOut.flush();
        out.write(builder.toString());
        out.newLine();
    }
}
