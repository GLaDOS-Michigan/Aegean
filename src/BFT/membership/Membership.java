// $Id: Membership.java 709 2011-06-29 13:19:15Z aclement $
package BFT.membership;

import BFT.Debug;
import BFT.Parameters;
import BFT.exec.ExecBaseNode;
import BFT.util.Role;

import javax.crypto.Mac;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Properties;

public class Membership {

    Role role;
    int id;
    int subId;
    Parameters parameters;
    String myPrivateKeyString;
    String myPublicKeyString;
    RSAPublicKey myPublicKey;
    RSAPrivateKey myPrivateKey;
    Principal[] filterNodes;
    Principal[] orderNodes;
    Principal[] execNodes;
    Principal[][] clientNodes;
    Principal[] verifierNodes;
    Principal[] myInterfaces;
    // TLR 2009.1.25: Support for different threads handling the network interaction with each role
    private Principal[] myFilterInterfaces;
    private Principal[] myOrderInterfaces;
    private Principal[] myExecInterfaces;
    private Principal[] myClientInterfaces;
    private Principal[] myVerifierInterfaces;
    private Mac myMac;

    private int[] clientToleratedCrashes;
    private int[] clientToleratedLiars;

    public Membership(String configFilename, Parameters parameters, Role _role, int _id) {
        this(configFilename, parameters, _role, _id, -1);
    }

    /**
     * This constructor receives 4 arguments:
     * configFilename : path to the config file
     * role : can be in the set {order, exec, client}
     * id : a number from 1 to k, where k is the maximum id possible for that role
     * sub_id : a second identifier, used primarily to distinguish between multiple replicas of the same client
     */
    public Membership(String configFilename, Parameters _parameters, Role _role, int _id, int _sub_id) {
        Security.addProvider(new de.flexiprovider.core.FlexiCoreProvider());

        role = _role;
        id = _id;
        subId = _sub_id;
        parameters = _parameters;


        Properties privKeyProp = new Properties();
        FileInputStream tmp = null;
        try {
            if (role.toString().matches("CLIENT")) {
                tmp = new FileInputStream("./keys/" + role.toString() + id + "." + subId + ".privk.properties");
                privKeyProp.load(tmp);
                tmp.close();
            } else {
                tmp = new FileInputStream("./keys/" + role.toString() + id + ".privk.properties");
                privKeyProp.load(tmp); //TODO instead of hash table, a well-defined class could be used
                tmp.close();
            }
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        myPrivateKeyString = privKeyProp.getProperty("PRIV");

        // Read properties file.
        Properties properties = new Properties();
        try {
            tmp = new FileInputStream(configFilename);
            properties.load(tmp);
            tmp.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        readConfiguration(properties, parameters); //TODO this is the place hashTable is used to populate parameters
                                                   //properties reflects exactly the file, why we don't populate from the file

        if (parameters.getFilterLiars() == 0 && parameters.getOrderLiars() == 0) {
            parameters.insecure = true;
        }

        if (parameters.digestType.equalsIgnoreCase("MD5")) {
            parameters.digestLength = 16;
        } else if (parameters.digestType.equalsIgnoreCase("SHA-256")) {
            parameters.digestLength = 32;
        } else if (parameters.digestType.equalsIgnoreCase("CRC32")) {
            parameters.digestLength = 8;
        } else {
            Debug.warning(true, "Unknown digestType %s", parameters.digestType);
        }

        if (parameters.executionDigestType.equalsIgnoreCase("MD5")) {
            parameters.executionDigestLength = 16;
        } else if (parameters.executionDigestType.equalsIgnoreCase("SHA-256")) {
            parameters.executionDigestLength = 32;
        } else if (parameters.executionDigestType.equalsIgnoreCase("CRC32")) {
            parameters.executionDigestLength = 4;
        } else {
            Debug.warning(true, "Unknown executionDigestType %s", parameters.executionDigestType);
        }


        System.out.println("filterCaching: " + parameters.filterCaching);
        System.out.println("filtered: " + parameters.filtered);
        System.out.println("useVerifier: " + parameters.useVerifier);
        System.out.println("filter:" + parameters.getFilterCrashes() + "+"
                + parameters.getFilterLiars() + "="
                + parameters.getFilterCount());
        System.out.println("order: " + parameters.getOrderCount());
        System.out.println("exec:  " + parameters.getExecutionCount());
        System.out.println("verifier:  " + parameters.getVerifierCount());
        System.out.println("sliceExecutionPipelineDepth: " + parameters.sliceExecutionPipelineDepth);
        System.out.println("replyCacheSize: " + parameters.replyCacheSize);
        System.out.println("level_debug: " + Parameters.level_debug);
        System.out.println("level_fine: " + Parameters.level_fine);
        System.out.println("level_info: " + Parameters.level_info);
        System.out.println("level_warning: " + Parameters.level_warning);
        System.out.println("level_error: " + Parameters.level_error);
        System.out.println("level_trace: " + parameters.level_trace);


        int offset = 0; // tells you from which column to read according to the role

        if (role.equals(Role.CLIENT)) {
            CheckIdWithinRange(parameters.getNumberOfClients());
            offset = 0;
        } else if (role.equals(Role.FILTER)) {
            CheckIdWithinRange(parameters.getFilterCount());
            offset = 1 + (id);
        } else if (role.equals(Role.ORDER)) {
            CheckIdWithinRange(parameters.getOrderCount());
            offset = 1 // number of client entries
                    + parameters.getFilterCount() // number of filter entries
                    + (id); // offset into the order entries
        } else if (role.equals(Role.EXEC)) {
            CheckIdWithinRange(parameters.getExecutionCount());
            offset = (1) // number of client entries;
                    + parameters.getFilterCount() // number of filter entries
                    + parameters.getOrderCount() // number of order entries
                    + (id);
        } // offset into the exec entries
        else if (role.equals(Role.VERIFIER)) {
            CheckIdWithinRange(parameters.getVerifierCount());
            offset = (1) // number of client entries;
                    + parameters.getFilterCount() // number of filter entries
                    + parameters.getOrderCount() // number of order entries
                    + parameters.getExecutionCount() // number of exec entries
                    + (id); // offset into the verify entries
        } else {
            System.err.println("Unknown role while parsing properties file");
            System.exit(0);
        }

        int interfaceCount = 1
                + parameters.getFilterCount()
                + parameters.getOrderCount()
                + parameters.getExecutionCount()
                + parameters.getVerifierCount();
        orderNodes = new Principal[parameters.getOrderCount()];
        execNodes = new Principal[parameters.getExecutionCount()];
        clientNodes = new Principal[parameters.getNumberOfClients()][];
        verifierNodes = new Principal[parameters.getVerifierCount()];
        myInterfaces = new Principal[interfaceCount];
        clientToleratedCrashes = new int[parameters.getNumberOfClients()];
        clientToleratedLiars = new int[parameters.getNumberOfClients()];
        if (parameters.filtered) {
            filterNodes = new Principal[parameters.getFilterCount()];
        } else {
            filterNodes = new Principal[0];
        }

        // Principle expects pubkey currently at split[split.length-1]. TODO important part
        // Last param used to be split[interfaceCount]

        for (int i = 0; i < parameters.getFilterCount(); i++) {
            String value = properties.getProperty(Role.FILTER + "." + i);
            String[] split = value.split(" ", 0);
            filterNodes[i] = new Principal(parameters, split[offset], split[split.length - 1]);
        }

        for (int i = 0; i < parameters.getOrderCount(); i++) {
            String value = properties.getProperty(Role.ORDER + "." + i);
            String[] split = value.split(" ", 0);
            orderNodes[i] = new Principal(parameters, split[offset], split[split.length - 1]);
        }


        for (int i = 0; i < parameters.getExecutionCount(); i++) {
            String value = properties.getProperty(Role.EXEC + "." + i);
            String[] split = value.split(" ", 0);
            execNodes[i] = new Principal(parameters, split[offset], split[split.length - 1]);
        }

        for (int i = 0; i < parameters.getVerifierCount(); i++) {
            String value = properties.getProperty(Role.VERIFIER + "." + i);
            String[] split = value.split(" ", 0);
            verifierNodes[i] = new Principal(parameters, split[offset], split[split.length - 1]);
        }

        for (int i = 0; i < parameters.getNumberOfClients(); i++) {
            if (properties.getProperty(Role.CLIENT + "." + i) != null) {    //backwards compatibility
                clientNodes[i] = new Principal[1];
                String value = properties.getProperty(Role.CLIENT + "." + i);
                String[] split = value.split(" ", 0);
                clientNodes[i][0] = new Principal(parameters, split[offset], split[split.length - 1]);
            } else {
                String toleratedCrashes = properties.getProperty(Role.CLIENT + "." + i + "." + "toleratedCrashes");
                String toleratedLiars = properties.getProperty(Role.CLIENT + "." + i + "." + "toleratedLiars");
                if (toleratedCrashes != null) {
                    clientToleratedCrashes[i] = Integer.parseInt(toleratedCrashes);
                }
                if (toleratedLiars != null) {
                    clientToleratedLiars[i] = Integer.parseInt(toleratedLiars);
                }
                // first, find how many replicas this client has (to verify the above replication parameters)
                int count = 0; //This is to check validity of the properties file. It should match with the formula below
                while (properties.getProperty(Role.CLIENT + "." + i + "." + count) != null) {
                    count++;
                }
                if (count != getClientReplicationCount(i)) {
                    BFT.Debug.kill("invalid client replication count: expected " + getClientReplicationCount(i) + " replicas, found " + count + " for client " + i);
                }
                clientNodes[i] = new Principal[count];//different principal for each client replica
                String value;
                for (int k = 0; (value = properties.getProperty(Role.CLIENT + "." + i + "." + k)) != null; k++) {
                    //value = properties.getProperty(Role.CLIENT + "." + i);
                    String[] split = value.split(" ", 0);
                    clientNodes[i][k] = new Principal(parameters, split[offset], split[split.length - 1]);
                }
            }
        }

        // The second part of the condition below is for backwards compatibility with CLIENT.id entries
        String extra = role.equals(Role.CLIENT) && properties.getProperty(role + "." + id) == null ? "." + subId : "";
        String value = properties.getProperty(role + "." + id + extra);
        //String valueWithoutSubid = properties.getProperty(role + "." + id);
        String[] split;
        if (value != null) {
            split = value.split(" ", 0);
        } else {
            throw new RuntimeException("Could not find property: " + role + "." + id + extra);
        }

        for (int i = 0; i < myInterfaces.length; i++) {     // don't include the private key, just the IP:port pairs
            myInterfaces[i] = new Principal(parameters, split[i], split[split.length - 1]);
        }
        myPublicKeyString = split[split.length - 1];
        myPublicKey = BFT.util.KeyGen.getPubKeyFromString(parameters, myPublicKeyString);
        myPrivateKey = BFT.util.KeyGen.getPrivKeyFromString(parameters, myPrivateKeyString);


        try {
            privKeyProp = new Properties();
            for (int i = 0; i < clientNodes.length; i++) { //For each client's each replica set the key
                for (int k = 0; k < clientNodes[i].length; k++) {
                    tmp = new FileInputStream("./keys/" + Role.CLIENT.toString() + i + "." + k + ".privk.properties");
                    privKeyProp.load(tmp);
                    tmp.close();
                    clientNodes[i][k].setMacKey(BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET")));
                }
            }
            for (int i = 0; i < filterNodes.length; i++) { //For each filter set the key
                tmp = new FileInputStream("./keys/" + Role.FILTER.toString() + i + ".privk.properties");
                privKeyProp.load(tmp);
                tmp.close();
                filterNodes[i].setMacKey(BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET")));
            }
            for (int i = 0; i < orderNodes.length; i++) {
                tmp = new FileInputStream("./keys/" + Role.ORDER.toString() + i + ".privk.properties");
                privKeyProp.load(tmp);
                tmp.close();
                orderNodes[i].setMacKey(BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET")));
            }
            for (int i = 0; i < execNodes.length; i++) {
                tmp = new FileInputStream("./keys/" + Role.EXEC.toString() + i + ".privk.properties");
                privKeyProp.load(tmp);
                tmp.close();
                execNodes[i].setMacKey(BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET")));
            }
            for (int i = 0; i < verifierNodes.length; i++) {
                tmp = new FileInputStream("./keys/" + Role.VERIFIER.toString() + i + ".privk.properties");
                privKeyProp.load(tmp);
                tmp.close();
                verifierNodes[i].setMacKey(BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET")));
            }
            // The second part of the condition below is for backwards compatibility with CLIENT.id entries
            String extraForClient = role.equals(Role.CLIENT) && properties.getProperty(role + "." + id) == null ? "." + subId : "";
            tmp = new FileInputStream("./keys/" + role.toString() + id + extraForClient + ".privk.properties");
            privKeyProp.load(tmp);
            tmp.close();
            myMac = BFT.util.KeyGen.getMacObjectfromString(privKeyProp.getProperty("SECRET"));
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        // TLR 2009.1.25: Support for different threads handling the network interaction with each role
        value = properties.getProperty(role + "." + id + extra);
        split = value.split(" ", 0);
        myOrderInterfaces = new Principal[parameters.getOrderCount()];
        myExecInterfaces = new Principal[parameters.getExecutionCount()];
        myVerifierInterfaces = new Principal[parameters.getVerifierCount()];

        myClientInterfaces = new Principal[1];
        myClientInterfaces[0] = new Principal(parameters, split[0], split[split.length - 1]);
        for (int i = 0; i < parameters.getOrderCount(); i++) {
            myOrderInterfaces[i] =
                    new Principal(parameters, split[1
                            + parameters.getFilterCount()
                            + i],
                            split[split.length - 1]);
        }
        for (int i = 0; i < parameters.getExecutionCount(); i++) {
            myExecInterfaces[i] =
                    new Principal(parameters, split[1
                            + parameters.getFilterCount()
                            + parameters.getOrderCount()
                            + i],
                            split[split.length - 1]);
        }
        for (int i = 0; i < parameters.getVerifierCount(); i++) {
            myVerifierInterfaces[i] =
                    new Principal(parameters, split[1
                            + parameters.getFilterCount()
                            + parameters.getOrderCount()
                            + parameters.getExecutionCount()
                            + i],
                            split[split.length - 1]);
        }
        if (parameters.filtered) {
            myFilterInterfaces = new Principal[parameters.getFilterCount()];
        } else {
            myFilterInterfaces = new Principal[0];
        }
        for (int i = 0; i < parameters.getFilterCount(); i++) {
            myFilterInterfaces[i] = new Principal(parameters, split[1 + i], split[split.length - 1]);
        }
    }

    public void CheckIdWithinRange(int range) {
        if (id < 0 || id > range) {
            Debug.warning(Debug.MODULE_EXEC, "Id is: %d. Should be between >=0 and <= %d\n", id, range);
            throw new RuntimeException("invalid range " + range + " for id " + id);
        }
    }

    public Principal[] getFilterNodes() {
        return filterNodes;
    }

    public Principal[] getOrderNodes() {
        return orderNodes;
    }

    public Principal[] getExecNodes() {
        return execNodes;
    }

    public Principal[][] getClientNodes() {
        return clientNodes;
    }

    public Principal[] getMyInterfaces() {
        return myInterfaces;
    }

    public Principal[] getVerifierNodes() {
        return verifierNodes;
    }

    public RSAPublicKey getMyPublicKey() {
        return myPublicKey;
    }

    public RSAPrivateKey getMyPrivateKey() {
        return myPrivateKey;
    }

    public Mac getMyMac() {
        return myMac;
    }

    public int getMyId() {
        return id;
    }

    public int getMySubId() {
        return subId;
    }

    public Role getMyRole() {
        return role;
    }

    // TLR 2009.1.25: Support for different threads handling the network interaction with each role
    public Principal[] getMyInterfaces(Role r) {
        switch (r) {
            case FILTER:
                return myFilterInterfaces;
            case ORDER:
                return myOrderInterfaces;
            case EXEC:
                return myExecInterfaces;
            case CLIENT:
                return myClientInterfaces;
            case VERIFIER:
                return myVerifierInterfaces;
            default:
                throw new RuntimeException("Unknown Role");
        }
    }

    public int getClientReplicationCount(int clientId) {
        if (clientId >= parameters.getNumberOfClients() || clientId < 0) {
            throw new RuntimeException("invalid clientId");
        }

//        System.out.println("primaryBackup: " + parameters.primaryBackup + ", isMiddle: " + parameters.isMiddleClient);
        if(parameters.primaryBackupClient && parameters.isMiddleClient) {
//            System.out.println("In primary backup, the client's replication count is 2");
            return 2;
        }

        return clientToleratedCrashes[clientId] +
                parameters.max(clientToleratedCrashes[clientId], clientToleratedLiars[clientId]) +
                1;
    }

    public int getClientQuorumSize(int clientId) {
        int quorumSize = Parameters.max(clientToleratedCrashes[clientId], clientToleratedLiars[clientId]) + 1;
//        System.out.println("using get client quorum size: " + quorumSize);
        return quorumSize;
    }

    public int getShimLayerQuorumSize(int clientId) {
        assert (parameters.isMiddleClient);
        if(parameters.normalMode)
            return 1;
        if(parameters.primaryBackupClient) {
//            System.out.println("In primary backup, the quorum is 2");
            return 2;
        }
        return clientToleratedCrashes[clientId] + clientToleratedLiars[clientId] + 1;
    }

    private void readConfiguration(Properties properties, Parameters parameters) {
        try {
            Class cls = BFT.Parameters.class;


            Field[] field = cls.getDeclaredFields();
            for (int i = 0; i < field.length; i++) {
                if (properties.getProperty(field[i].getName()) == null) {
                    continue;
                }
                if (field[i].getType() == int.class) {
                    field[i].setInt(parameters, Integer.parseInt(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == long.class) {
                    field[i].setLong(parameters, Long.parseLong(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == byte.class) {
                    field[i].setByte(parameters, Byte.parseByte(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == char.class) {
                    field[i].setChar(parameters,
                            properties.getProperty(field[i].getName()).charAt(0));
                } else if (field[i].getType() == short.class) {
                    field[i].setShort(parameters, Short.parseShort(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == boolean.class) {
                    field[i].setBoolean(parameters, Boolean.parseBoolean(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == float.class) {
                    field[i].setFloat(parameters, Float.parseFloat(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == double.class) {
                    field[i].setDouble(parameters, Double.parseDouble(
                            properties.getProperty(field[i].getName())));
                } else if (field[i].getType() == String.class) {
                    field[i].set(parameters, properties.getProperty(field[i].getName()));
                } else {
                    System.out.println("Unrecognized type " + field[i]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

