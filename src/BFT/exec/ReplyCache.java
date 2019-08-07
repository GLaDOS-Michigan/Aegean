/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BFT.exec;

import BFT.Debug;
import BFT.Parameters;
import merkle.MerkleTreeInstance;
import merkle.MerkleTreeObjectImp;
import merkle.wrapper.MTArrayWrapper;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author yangwang
 */
public class ReplyCache implements Serializable {

    public static class ReplyInfo extends MerkleTreeObjectImp implements Serializable {

        private int clientId;
        private int subId;
        private long requestId;
        private long seqNo = -1L;
        private MTArrayWrapper<byte[]> replyWrapper = new MTArrayWrapper<byte[]>(new byte[0]);

        public ReplyInfo() {
            MerkleTreeInstance.add(this.replyWrapper);
        }

        private ReplyInfo(boolean isCopy) {
        }

        public void copyObject(Object dst) {
            ReplyInfo target = (ReplyInfo) dst;
            target.clientId = this.clientId;
            target.subId = this.subId;
            target.requestId = this.requestId;
            target.seqNo = this.seqNo;
            target.replyWrapper = this.replyWrapper;
        }

        public Object cloneObject() {
            ReplyInfo target = new ReplyInfo(true);
            target.clientId = this.clientId;
            target.subId = this.subId;
            target.requestId = this.requestId;
            target.seqNo = this.seqNo;
            target.replyWrapper = this.replyWrapper;
            return target;
        }

        public void setInfo(int clientId, int subId, long requestId, long seqNo, byte[] reply) {
            MerkleTreeInstance.update(this);
            this.clientId = clientId;
            this.subId = subId;
            this.requestId = requestId;
            this.seqNo = seqNo;
            MerkleTreeInstance.update(this.replyWrapper);
            this.replyWrapper.setArray(reply);
        }

        public int getClientId() {
            return this.clientId;
        }

        public int getSubId() {
            return this.subId;
        }

        public long getRequestId() {
            return this.requestId;
        }

        public long getSeqNo() {
            return this.seqNo;
        }

        public byte[] getReply() {
            return this.replyWrapper.getArray();
        }
    }

    private int replyCacheSize = 10;
    private ReplyInfo[] replies = null;
    private ReplyInfo[][] historyReplies = null;

    private byte[] historyHash;
    private boolean[] replyVerified;
    private Parameters parameters;

    public ReplyCache(Parameters param) {
        parameters = param;
        historyHash = new byte[parameters.digestLength];
        replyVerified = new boolean[parameters.getNumberOfClients()];
        replies = new ReplyInfo[parameters.getNumberOfClients()];
        replyCacheSize = parameters.replyCacheSize;
        historyReplies = new ReplyInfo[parameters.getNumberOfClients()][replyCacheSize];

        MerkleTreeInstance.addRoot(replies);
        for (int i = 0; i < replies.length; i++) {
            replies[i] = new ReplyInfo();
            MerkleTreeInstance.addRoot(replies[i]);
        }
        // TODO make debug statements
        System.out.println("Reply cache size! " + replyCacheSize);
        System.out.println("historyReplies.length" + historyReplies.length);
        for (int i = 0; i < historyReplies.length; i++) {
            for (int j = 0; j < replyCacheSize; j++) {
                historyReplies[i][j] = new ReplyInfo();
                MerkleTreeInstance.addRoot(historyReplies[i][j]);
            }
        }
        MerkleTreeInstance.addRoot(historyHash);
        reset();
    }

    // TODO make debug statements with correct module (not exec)
    public void printReplies() {
        Debug.debug(Debug.MODULE_EXEC, "Printing Reply Cache...");
        for (ReplyInfo reply : replies) {
            Debug.debug(Debug.MODULE_EXEC, "SeqNo: %d", reply.getSeqNo());
        }
    }

    // Resets the verified flags in the case of a rollback
    public synchronized void reset() {
        for (int i = 0; i < replyVerified.length; i++) {
            replyVerified[i] = true;
        }
    }

    public synchronized void getLastRequestId(long[] lastRequestId) {
        for (int i = 0; i < this.replies.length; i++) {
            lastRequestId[i] = this.replies[i].getRequestId();
        }
    }

    public void addReply(int clientId, int subId, long requestId, long seqNo, byte[] reply) {
        synchronized (replies[clientId]) {
            replies[clientId].setInfo(clientId, subId, requestId, seqNo, reply);
            historyReplies[clientId][(int) (requestId % replyCacheSize)]
                    .setInfo(clientId, subId, requestId, seqNo, reply);
            Debug.debug(Debug.MODULE_EXEC, "put reply for (%d,%d) subId %d into reply cache. reply=%s\n",
                    clientId, requestId, subId, util.UnsignedTypes.bytesToHexString(reply));
            replyVerified[clientId] = false;
        }
    }

    public boolean isReplyVerified(int clientId) {
        return replyVerified[clientId];
    }

    public void setReplyVerified(int clientId) {
        this.replyVerified[clientId] = true;
    }

    public ReplyInfo getLastReply(int clientId) {
        return replies[clientId];
    }

    public ReplyInfo getReplyForRequestID(long requestId, int clientId) {
        synchronized (replies[clientId]) {
            if (requestId >= replies[clientId].getRequestId()) {
                Debug.debug(Debug.MODULE_EXEC, "reply for client %d req %d is not cached yet.\n",
                        clientId, requestId);
                return null;
            } else if (requestId < replies[clientId].getRequestId() - replyCacheSize) {
                Debug.debug(Debug.MODULE_EXEC, "reply for client %d req %d is too old and has been removed.\n",
                        clientId, requestId);
                return null;
            } else {
                ReplyInfo reply = historyReplies[clientId][(int) (requestId % replyCacheSize)];
                if (reply.getRequestId() != requestId) {
                    Debug.debug(Debug.MODULE_EXEC, "request id mismatch. cached: %d requested: %d\n",
                            reply.getRequestId(), requestId);
                }
                return reply;
            }
        }
    }

    public ArrayList<ReplyInfo> getReplies(long seqNo) {
        //Clear possible stale info if exists
        ArrayList<ReplyInfo> ret = new ArrayList<ReplyInfo>();
        for (int i = 0; i < replyVerified.length; i++) {
            if (!replyVerified[i] && replies[i].getSeqNo() <= seqNo) {
                ret.add(replies[i]);
            }
        }
        return ret;
    }

    public byte[] getHistoryHash() {
        return this.historyHash;
    }

    public void setHistoryHash(byte[] hash) {
        MerkleTreeInstance.update(this.historyHash);
        System.arraycopy(hash, 0, historyHash, 0, parameters.digestLength);
    }
}
