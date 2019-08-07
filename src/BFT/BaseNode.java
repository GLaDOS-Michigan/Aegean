// $Id: BaseNode.java 722 2011-07-04 09:24:35Z aclement $

package BFT;


import Applications.tpcw_webserver.rbe.args.Arg;
import BFT.membership.Membership;
import BFT.membership.Principal;
import BFT.messages.*;
import BFT.network.MessageHandler;
import BFT.network.NetworkSender;
import BFT.order.OrderBaseNode;
import BFT.util.Role;

import javax.crypto.Mac;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Calendar;


abstract public class BaseNode extends Throwable implements MessageHandler {

    transient protected Membership members;

    transient protected NetworkSender sendNet = null;

    transient protected Parameters parameters;

    transient protected MessageFactory factory;

    transient int[] _filterReplicas;

    transient int[] _orderReplicas;

    transient int[] _otherOrderReplicas;

    transient int[] _executionReplicas;

    transient int[] _otherExecutionReplicas;

    public BaseNode(String membershipFile, String myId) {
        throw new RuntimeException("DEPRECATED: Please use the" +
                " BaseNode(String membershipFile," +
                " Role myRole, int myId) constructor");
    }

    /* providing a constructor without subId for backwards compatibility */
    public BaseNode(String membershipFile, Role myRole, int myId) {
        this(membershipFile, myRole, myId, -1);
    }

    public BaseNode(String membershipFile, Role myRole, int myId, int mySubId) {

        parameters = new Parameters();
        // populate the membership list
        members = new Membership(membershipFile, parameters, myRole, myId, mySubId); //To set identity of other nodes and interfaces to them
        factory = new MessageFactory(parameters);
        // TODO: fill in all the appropriate keys

        ////System.out.println("registered ServerSocketChannels");

    }

    public Membership getMembership() {
        return members;
    }


    public Mac getMyMac() {
        return members.getMyMac();
    }

    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Functions to get the Mac keys for specific nodes *
     */
    public Mac getClientMac(int id, int subId) {
        //System.out.println("fdjskalfjdsak");
        //java.lang.Throwable a = new java.lang.Throwable();
        //a.printStackTrace();
        Debug.info(Debug.MODULE_BASENODE, "IDS: %d %d", id, subId);
        return members.getClientNodes()[id][subId].getMacKey();
    }

    public Mac[][] getClientMacs() {
        Principal[][] clientNodes = members.getClientNodes();
        int arrayLength = clientNodes.length;
        Mac[][] clientMacs = new Mac[arrayLength][];
        for (int i = 0; i < arrayLength; i++) {
            int subarraylength = clientNodes[i].length;
            clientMacs[i] = new Mac[subarraylength];
            for (int j = 0; j < subarraylength; i++) {
                clientMacs[i][j] = clientNodes[i][j].getMacKey();
            }
        }
        return clientMacs;
    }

    public Mac getFilterReplicaMac(int id) {
        return members.getFilterNodes()[id].getMacKey();
    }

    public Mac[] getFilterMacs() {
        //System.out.println("\t\t2");
        int arrayLength = members.getFilterNodes().length;
        //System.out.println("\t\t2.1");
        Mac[] filterMacs = new Mac[arrayLength];
        //System.out.println("\t\t2.2");
        for (int i = 0; i < arrayLength; i++) {
            //System.out.println("\t\t\t2.2."+i);
            filterMacs[i] = getFilterReplicaMac(i);
        }
        //System.out.println("\t\t2.3");
        return filterMacs;
    }


    public Mac getOrderReplicaMac(int id) {
        return members.getOrderNodes()[id].getMacKey();
    }

    public Mac[] getOrderMacs() {
        int arrayLength = members.getOrderNodes().length;
        Mac[] orderMacs = new Mac[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            orderMacs[i] = getOrderReplicaMac(i);
        }
        return orderMacs;
    }

    public Mac getExecutionReplicaMac(int id) {
        return members.getExecNodes()[id].getMacKey();
    }

    public Mac[] getExecutionMacs() {
        int arrayLength = members.getExecNodes().length;
        Mac[] execMacs = new Mac[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            execMacs[i] = getExecutionReplicaMac(i);
        }
        return execMacs;
    }

    public Mac getVerifierReplicaMac(int id) {
        return members.getVerifierNodes()[id].getMacKey();
    }

    public Mac[] getVerifierMacs() {
        int arrayLength = members.getVerifierNodes().length;
        Mac[] verifyMacs = new Mac[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            verifyMacs[i] = getVerifierReplicaMac(i);
        }
        return verifyMacs;
    }

    /**
     * Functions to get the public keys for specific nodes *
     */
    public RSAPublicKey getClientPublicKey(int id, int subId) {
        return members.getClientNodes()[id][subId].getPublicKey();
    }

    public RSAPublicKey getOrderReplicaPublicKey(int id) {
        return members.getOrderNodes()[id].getPublicKey();
    }

    public RSAPublicKey getExecutionReplicaPublicKey(int id) {
        return members.getExecNodes()[id].getPublicKey();
    }

    public RSAPublicKey getVerifierReplicaPublicKey(int id) {
        return members.getVerifierNodes()[id].getPublicKey();
    }

    /**
     * Functions to get my private key *
     */

    public RSAPrivateKey getMyPrivateKey() {
        return members.getMyPrivateKey();
    }

    public RSAPublicKey getMyPublicKey() {
        return members.getMyPublicKey();
    }

    public void sendToFilterReplica(byte m[], int id) {
        if (sendNet != null) {
            sendNet.send(m, Role.FILTER, id);
        } else {
            throw new RuntimeException("Send to unitialized sendNet " + id);
        }
    }

    public void sendToAllFilterReplicas(byte[] m) {
        if (_filterReplicas == null) {
            _filterReplicas = new int[parameters.getFilterCount()];
            for (int i = 0; i < parameters.getFilterCount(); i++)
                _filterReplicas[i] = i;
        }
        sendNet.send(m, Role.FILTER, _filterReplicas);

        //        for (int i = 0; i < BFT.Parameters.getFilterCount(); i++) {
        //   sendToFilterReplica(m, i);
        //	    sendToFilterReplica(m, (i+getMyIndex())%BFT.Parameters.getFilterCount());
        // }
    }


    /**
     * Functions to communicate with the order replicas *
     */
    public void sendToOrderReplica(byte m[], int id) {
        if (sendNet != null) {
            sendNet.send(m, Role.ORDER, id);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    public void sendToAllOrderReplicas(byte[] m) {
        if (sendNet != null) {
            if (_orderReplicas == null) {
                _orderReplicas = new int[parameters.getOrderCount()];
                for (int i = 0; i < parameters.getOrderCount(); i++) {
                    _orderReplicas[i] = i;
                    //sendToOrderReplica(m, i);
//                    System.err.println("sent to " + i);
                }
            }
            sendNet.send(m, Role.ORDER, _orderReplicas);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }


    public void sendToOtherOrderReplicas(byte[] m, int ind) {
        if (sendNet != null) {
            if (_otherOrderReplicas == null) {
                _otherOrderReplicas =
                        new int[parameters.getOrderCount() - 1];
                for (int i = 0, j = 0; i < parameters.getOrderCount(); i++) {
                    if (i != ind) {
                        _otherOrderReplicas[j++] = i;
                        //sendToOrderReplica(m, i);
                    }
                }
            }
            sendNet.send(m, Role.ORDER, _otherOrderReplicas);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }


    /**
     * Functions to communicate with the execution replicas *
     */
    public void sendToExecutionReplica(byte m[], int id) {
        if (sendNet != null) {
            sendNet.send(m, Role.EXEC, id);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    public void sendToAllExecutionReplicas(byte m[]) {
        if (sendNet != null) {
            if (_executionReplicas == null) {
                _executionReplicas =
                        new int[parameters.getExecutionCount()];
                for (int i = 0; i < parameters.getExecutionCount(); i++) {
                    _executionReplicas[i] = i;
                    //sendToExecutionReplica(m, i);
                }
            }
            sendNet.send(m, Role.EXEC, _executionReplicas);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    public void sendToOtherExecutionReplicas(byte[] m, int index) {
        if (sendNet != null) {
            if (_otherExecutionReplicas == null) {
                _otherExecutionReplicas =
                        new int[parameters.getExecutionCount() - 1];
                for (int i = 0, j = 0;
                     i < parameters.getExecutionCount(); i++) {
                    if (i != index) {
                        _otherExecutionReplicas[j++] = i;
                        //sendToExecutionReplica(m, i);
                    }
                }
            }
            sendNet.send(m, Role.EXEC, _otherExecutionReplicas);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    /**
     * Functions to communicate with the verifier replicas *
     */
    public void sendToVerifierReplica(byte m[], int id) {
        if (sendNet != null) {
            sendNet.send(m, Role.VERIFIER, id);
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    public void sendToAllVerifierReplicas(byte m[]) {
        if (sendNet != null) {
            for (int i = 0; i < parameters.getVerifierCount(); i++) {
                sendToVerifierReplica(m, i);
            }
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    public void sendToOtherVerifierReplicas(byte[] m, int index) {
        if (sendNet != null) {
            for (int i = 0; i < parameters.getVerifierCount(); i++) {
                if (i != index) {
                    sendToVerifierReplica(m, i);
                }
            }
        } else {
            throw new RuntimeException("Send to unitialized sendNet");
        }
    }

    /**
     * Functions to communciate with the client *
     */
    public void sendToClient(byte m[], int id, int subId) {
        if (sendNet != null) {
            sendNet.send(m, Role.CLIENT, id, subId);
        } else {
            throw new RuntimeException("Send to an unitialized client net");
        }
    }

    public void setNetwork(NetworkSender net) {
        sendNet = net;
    }

    /**
     * Listen to appropriate sockets and call handle on all
     * appropriate incoming messages
     */
    public void start() {
        if (sendNet == null) {
            throw new RuntimeException("dont have a network");
        } else {
            //System.out.println("wtf");
        }
    }

    public void stop() {

        //throw new RuntimeException("Not yet implemented");
    }


    //public boolean validateClientMacMessage(MacMessage mm) {
    //    Mac key = getClientMac((int) mm.getSender());
    //    return _validateMacMessage(mm, key);
    //}

    public boolean validateOrderMacMessage(MacMessage mm) {
        Mac key = getOrderReplicaMac((int) mm.getSender());
        return _validateMacMessage(mm, key);
    }

    public boolean validateExecMacMessage(MacMessage mm) {
        Mac key = getExecutionReplicaMac((int) mm.getSender());
        return _validateMacMessage(mm, key);
    }

    public boolean validateFilterMacMessage(MacMessage mm) {
        Mac key = getFilterReplicaMac((int) mm.getSender());
        return _validateMacMessage(mm, key);
    }

    public boolean validateVerifierMacMessage(MacMessage mm) {
        Mac key = getVerifierReplicaMac((int) mm.getSender());
        return _validateMacMessage(mm, key);
    }


    protected boolean _validateMacMessage(MacMessage mm, Mac key) {
        return validateBytes(mm.getBytes(), mm.getAuthenticationStartIndex(),
                mm.getAuthenticationLength(),
                key, mm.getMacBytes());
    }


    public boolean validateClientMacArrayMessage(MacArrayMessage mam,
                                                 int index) {
        return _validateMacArrayMessage(mam,
                getClientMac(mam.getSender(), mam.getSubId()),
                index);
    }


    public boolean validateFilterMacArrayMessage(MacArrayMessage mam,
                                                 int index) {
//        System.err.println("MAM: " + mam + ", macBytes: " + mam.getMacBytes(index));
        return _validateMacArrayMessage(mam,
                getFilterReplicaMac(mam.getSender()),
                index);
    }

    public boolean validateOrderMacArrayMessage(MacArrayMessage mam,
                                                int index) {
        return _validateMacArrayMessage(mam,
                getOrderReplicaMac(mam.getSender()),
                index);
    }

    public boolean validateExecMacArrayMessage(MacArrayMessage mam,
                                               int index) {
        return _validateMacArrayMessage(mam,
                getExecutionReplicaMac(mam.getSender()),
                index);

    }

    public boolean validateVerifierMacArrayMessage(MacArrayMessage mam,
                                                   int index) {
        return _validateMacArrayMessage(mam,
                getVerifierReplicaMac(mam.getSender()),
                index);

    }

    protected boolean _validateMacArrayMessage(MacArrayMessage mam,
                                               Mac[] keys, int index) {
        Digest ad = mam.getAuthenticationDigest();
        boolean retVal = validateBytes(mam.getBytes(),
                mam.getAuthenticationStartIndex(),
                mam.getAuthenticationLength(),
                keys[(int) mam.getSender()],
                mam.getMacBytes(index));
        return retVal;
    }

    protected boolean _validateMacArrayMessage(MacArrayMessage mam,
                                               Mac key, int index) {
        Digest ad = mam.getAuthenticationDigest();
        return validateBytes(mam.getBytes(),
                mam.getAuthenticationStartIndex(),
                mam.getAuthenticationLength(),
                key,
                mam.getMacBytes(index));
    }


    /**
     * Check that a threshold of folks authenticate this message for
     * you *
     */
    public boolean validateFullFilterMacSignatureMessage(MacSignatureMessage mam,
                                                         int thresh,
                                                         int orderIndex) {
        Mac[] keys = getFilterMacs();
        int count = 0;
        int me = orderIndex;
        Digest ad = mam.getAuthenticationDigest();
        for (int i = 0; i < keys.length && count < thresh; i++) {
            MacBytes[] mb = mam.getMacArray(i, parameters.getOrderCount());
//            System.err.println("bytes: " + Arrays.toString(mam.getBytes()) + ",startIndex: " + mam.getAuthenticationStartIndex() + ". lenght: " + mam.getAuthenticationLength()
//                                + "keys: " + Arrays.toString(keys) + ", i: " + i);
            if (validateBytes(mam.getBytes(),
                    mam.getAuthenticationStartIndex(),
                    mam.getAuthenticationLength(),
                    keys[i], mb[me]))
            {
//                System.err.println("Good one: " + i);
                count++;
            }
            else {
//                System.err.println("The problematic one: " + i);
            }

        }
//        System.err.println("count: " + count + ", thresh: " + thresh);
        if (count < thresh)
        {
            BFT.Debug.kill("OH SHIT, bad things");
        }
        return count >= thresh;
    }

    /**
     * check that the specified sender authenticates this message for you *
     */
    public boolean validatePartialFilterMacSignatureMessage(MacSignatureMessage mam,
                                                            int signer) {
        Mac[] keys = getFilterMacs();
        MacBytes[] mb = mam.getMacArray(signer,
                parameters.useVerifier ? parameters.getExecutionCount() : parameters.getOrderCount());
        Digest ad = mam.getAuthenticationDigest();
//        System.err.println("last paramater: " + mb[members.getMyId()] + ", id: " + members.getMyId());
//        System.err.println("bytes: " + Arrays.toString(mam.getBytes()) + ",startIndex: " + mam.getAuthenticationStartIndex() + ". lenght: " + mam.getAuthenticationLength()
//                + "keys: " + Arrays.toString(keys) + ", i: " + signer);
        boolean retVal = validateBytes(mam.getBytes(),
                mam.getAuthenticationStartIndex(),
                mam.getAuthenticationLength(),
                keys[signer],
                mb[members.getMyId()]);
        return retVal;
    }

    /**
     * message to be authenticated and index of the receiving node
     **/
    public void authenticateClientMacMessage(MacMessage mm, int index, int subId) {
        Mac key = getClientMac(index, subId);
        _authenticateMacMessage(mm, key);
    }

    public void authenticateOrderMacMessage(MacMessage mm, int index) {
        Mac key = getOrderReplicaMac(index);
        _authenticateMacMessage(mm, key);
    }

    public void authenticateExecMacMessage(MacMessage mm, int index) {
        Mac key = getExecutionReplicaMac(index);
        _authenticateMacMessage(mm, key);
    }

    public void authenticateVerifierMacMessage(MacMessage mm, int index) {
        Mac key = getVerifierReplicaMac(index);
        _authenticateMacMessage(mm, key);
    }

    public void _authenticateMacMessage(MacMessage mm, Mac key) {
        MacBytes mb = authenticateBytes(mm.getBytes(),
                mm.getAuthenticationStartIndex(),
                mm.getAuthenticationLength(), key);
        mm.setMacBytes(mb);
    }

    protected void _authenticateMacArrayMessage(MacArrayMessage mam,
                                                Mac[] keys) {
        Digest ad = mam.getAuthenticationDigest();
        for (int i = 0; i < keys.length; i++) {
            MacBytes mb =
                    authenticateBytes(ad.getBytes(), 0,
                            ad.getSize(),
                            keys[i]);
            mam.setMacBytes(i, mb);
        }

    }

    public void authenticateOrderMacArrayMessage(MacArrayMessage mam) {
        Mac[] keys = getOrderMacs();
        _authenticateMacArrayMessage(mam, keys);
    }

    public void authenticateFilterMacArrayMessage(MacArrayMessage mam) {
        Mac[] keys = getFilterMacs();
        _authenticateMacArrayMessage(mam, keys);
    }

    public void authenticateExecMacArrayMessage(MacArrayMessage mam) {
        Mac[] keys = getExecutionMacs();
        _authenticateMacArrayMessage(mam, keys);
    }

    public void authenticateVerifierMacArrayMessage(MacArrayMessage mam) {
        Mac[] keys = getVerifierMacs();
        _authenticateMacArrayMessage(mam, keys);
    }

    public void authenticateOrderMacSignatureMessage(MacSignatureMessage msm,
                                                     int index) {
        Mac keys[] = getOrderMacs();
        MacBytes mb[] = new MacBytes[parameters.getOrderCount()];
//        System.err.println("before msm.getAuthenticationDigest: " + Arrays.toString(msm.getBytes()));

        msm.getAuthenticationDigest();
        // is the sender a filter or order node
        int columns = parameters.getOrderCount();
//        System.err.println("before for loop: " + Arrays.toString(msm.getBytes()));
        for (int i = 0; i < mb.length; i++) {
            mb[i] = authenticateBytes(msm.getBytes(),
                    msm.getAuthenticationStartIndex(),
                    msm.getAuthenticationLength(),
                    keys[i]);
        }
//        System.err.println("after for loop: " + Arrays.toString(msm.getBytes()));

        msm.setMacArray(index, mb);
        MacBytes mb2[] = msm.getMacArray(index, parameters.getOrderCount());
    }

    public void authenticateExecutionMacSignatureMessage(MacSignatureMessage msm,
                                                         int index) {
        Mac keys[] = getExecutionMacs();
        MacBytes mb[] = new MacBytes[parameters.getExecutionCount()];
        msm.getAuthenticationDigest();
        // is the sender a filter or order node
        int columns = parameters.getExecutionCount();
        for (int i = 0; i < mb.length; i++) {
            mb[i] = authenticateBytes(msm.getBytes(),
                    msm.getAuthenticationStartIndex(),
                    msm.getAuthenticationLength(),
                    keys[i]);
        }

        msm.setMacArray(index, mb);
    }


    /**
     * @param contents The bytes with which to generate a MAC
     * @param key      The initialized {@link javax.crypto.Mac} object used to generate a MAC
     * @return The {@link MacBytes} object containing the freshly generated MAC
     */
    public MacBytes authenticateBytes(byte[] contents, Mac key) {
        return authenticateBytes(contents, 0, contents.length, key);
        //     	MacBytes retBytes = null;
        //     	retBytes = new MacBytes(key.doFinal(contents));
        //     	return retBytes;
    }

    /**
     * @param contents The bytes with which to generate a MAC
     * @param offset   The offset in bytes into contents where the data starts
     * @param len      The length of the data in bytes
     * @param key      The initialized {@link javax.crypto.Mac} object used to generate a MAC
     * @return The {@link MacBytes} object containing the freshly generated MAC
     */
    public MacBytes authenticateBytes(byte[] contents, int offset, int len, Mac key) {
        synchronized (key) {
            MacBytes retBytes = null;
            if (parameters.insecure) {
                retBytes = new MacBytes();
            } else {
                key.update(contents, offset, len);
                retBytes = new MacBytes(key.doFinal());
            }
            return retBytes;
        }
    }

    /**
     * @param contents The bytes that macBytes refers to
     * @param key      The initialized {@link javax.crypto.Mac} object used to generate a MAC
     * @param macBytes The {@link MacBytes} containing the MAC to be verified
     * @return True macBytes represents the MAC generated by key over contents
     */
    public boolean validateBytes(byte[] contents, Mac key, MacBytes macBytes) {
        return validateBytes(contents, 0, contents.length, key, macBytes);
    }

    /**
     * @param contents The bytes that macBytes refers to
     * @param offset   The offset in bytes into contents where the data starts
     * @param len      The length of the data in bytes
     * @param key      The initialized {@link javax.crypto.Mac} object used to generate a MAC
     * @param macBytes The {@link MacBytes} containing the MAC to be verified
     * @return True macBytes represents the MAC generated by key over contents
     */
    protected boolean validateBytes(byte[] contents, int offset, int len, Mac key, MacBytes macBytes) {
        boolean retVal = false;
//        System.err.println("validateBytes");
        if (parameters.insecure) {
//            System.err.println("seems return true");
            retVal = true;
        } else {
            //	    key.update(contents, offset, len);
            //	    MacBytes answer = new MacBytes(key.doFinal());
            MacBytes answer = authenticateBytes(contents, offset, len, key);
            retVal = macBytes.equals(answer);
            MacBytes emptyArray = new MacBytes();
//            System.err.println("MacBytes: " + macBytes + ", answer: " + answer + ", retVal: " +retVal + ", empty: " + emptyArray);
//            if(macBytes.equals(emptyArray)){//throw exception to check stack trace
//                try {
//                    throw new Exception();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
        }
        return retVal;
    }

    public void printTime(boolean isStart) {
        StackTraceElement[] elements = getStackTrace();
        Calendar now = Calendar.getInstance();
        String startEnd = isStart ? "Starting " : "Ending   ";
        String toPrint = now.getTimeInMillis() + ": " +
                startEnd + elements[0].getClassName() + "." + elements[0].getMethodName();
        System.err.println(toPrint);
    }

    public Membership getMembers() {
        return members;
    }
}
