package BFT.messages;

import Applications.jetty.websocket.jsr356.annotations.Param;
import BFT.Debug;
import BFT.exec.ExecBaseNode;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class GroupClientRequests implements RequestCore, Serializable {

    transient Set<Integer> clients;
    transient byte[] bytes;
    byte[][] requests;
    byte[] stateHash;
    int numberOfNestedRequestInTheSlice;
    int newNestedGroupNo = 0; //TODO ???

    public GroupClientRequests(int groupSize, int newNestedGroupNo, int numThreads) {
        numberOfNestedRequestInTheSlice = groupSize;
        clients = new HashSet<>(groupSize);
        requests = new byte[numThreads][];
        this.newNestedGroupNo = newNestedGroupNo;
    }

    public boolean addRequest(byte[] req, int clientId) {
        int addResult = atomicSetAddOperation(clientId);
//        System.out.println("addResult: " + addResult);
        if(addResult > 0) {
            requests[clientId%requests.length] = req;
            if(addResult == numberOfNestedRequestInTheSlice) {
                return true;
            }
        }

        return false;
    }

    public byte[] serialize() {
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            try(ObjectOutputStream o = new ObjectOutputStream(b)) {
                o.writeObject(this);
            }

            return  b.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("Something wrong with serializing GroupClientRequests");
        return null;
    }

    public static GroupClientRequests deserialize(byte[] bytes) {
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return (GroupClientRequests) o.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("Something wrong with deserializing GroupClientRequests");
        return null;
    }

    public synchronized int atomicSetAddOperation(int clientId) {
        if(clients.add(clientId))
        {
            return clients.size();
        }

        return -1;
    }

    @Override
    public int getSendingClient() {
        throw new RuntimeException("getSendingClient is not implemented for GroupCLientRequests");
    }

    @Override
    public int getSubId() {
        throw new RuntimeException("getSubId is not implemented for GroupCLientRequests");
    }

    @Override
    public long getRequestId() {
        throw new RuntimeException("getRequestId is not implemented for GroupCLientRequests");
    }

    @Override
    public int getTag() {
        return MessageTags.BatchSuggestion;
    }

    @Override
    public long getGCId() {
        throw new RuntimeException("getGCId is not implemented for GroupCLientRequests");
    }

    @Override
    public byte[] getCommand() {
        throw new RuntimeException("getCommand is not implemented for GroupCLientRequests");
    }

    @Override
    public Entry getEntry() {
        throw new RuntimeException("getEntry is not implemented for GroupCLientRequests");
    }

    @Override
    public byte[] getBytes() {
        if(bytes == null) {
            bytes = serialize();
        }
        return bytes;
    }

    @Override
    public int getTotalSize() {
        return getBytes().length;
    }

    public String toString() {
        String s = "GroupSize: " + numberOfNestedRequestInTheSlice + ", groupNo: " + newNestedGroupNo + ": ";
        for(int  i = 0; i < requests.length; i++) {
            s += "request " + (i+1) + ": " + Arrays.toString(requests[i]) + ", ";
        }

        s += "\n stateHash: " + Arrays.toString(stateHash);
        return s;
    }

    public int getNewNestedGroupNo() {
        return newNestedGroupNo;
    }

    public int getNumberOfNestedRequestInTheSlice() {
        return numberOfNestedRequestInTheSlice;
    }

    public byte[][] getRequests() {
        return requests;
    }

    public boolean matches(GroupClientRequests entry) {
        boolean same = entry.requests.length == requests.length;

        for(int i = 0; same && i < requests.length; i++) {
            if(!compareRequests(entry.requests[i], requests[i])) {
                same = false;
            }
        }

        return same;
    }


    private boolean compareRequests(byte[] bytes, byte[] bytes1) {
        //return true;
        //TODO implement think about nulls as well
        if(bytes == null && bytes1 == null) {
            return true;
        }
        else if(bytes == null || bytes1 == null)
        {
                throw new RuntimeException("unexpectedly different commands");
        }

        RequestCore rc = new FilteredRequestCore(bytes, ExecBaseNode.singletonExec.getParameters());
        RequestCore rc1 = new FilteredRequestCore(bytes1, ExecBaseNode.singletonExec.getParameters());

        if(Arrays.equals(rc.getCommand(), rc1.getCommand())) {
            return true;
        }
        else
        {
           throw new RuntimeException("unexpectedly different commands");
        }
    }

    public void setStateHash(byte[] stateHash) {
        this.stateHash = stateHash;
    }

    public byte[] getStateHash() {
        return stateHash;
    }
}
