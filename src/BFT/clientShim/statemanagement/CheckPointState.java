
package BFT.clientShim.statemanagement;

import BFT.Parameters;
import BFT.messages.Reply;
import util.UnsignedTypes;

import java.util.ArrayList;

public class CheckPointState {
    protected ArrayList<Reply> replies;
    protected Parameters parameters;

    // start sequence number included in this cp
    protected long startSeqNum;

    // next sequence number to be included 
    protected long nextSeqNum;

    public CheckPointState(Parameters param) {
        this(param, 1);
    }

    public CheckPointState(Parameters param, long seqid) {
        this.parameters = param;
        /// FIXME: should be checkpoint interval 
        replies = new ArrayList<Reply>(100);
        startSeqNum = seqid;
        nextSeqNum = seqid;
    }

    public CheckPointState(Parameters param, CheckPointState cps) {
        this.parameters = param;
        replies = new ArrayList<Reply>((int) cps.getSize());
        startSeqNum = cps.getStartSeqNum();
        nextSeqNum = cps.getNextSeqNum();
        for (long i = 0; i < cps.getSize(); i++) {
            replies.add(cps.fetchReply(new Long(i + cps.getStartSeqNum())));
        }
    }

    public CheckPointState(Parameters param, byte[] bytes) {

        this.parameters = param;
        int offset = 0;
        // read the startseq no
        //System.out.println("\tCheckPoints from bytes: sum size " + bytes.length);
        //System.out.println("\tCheckPoints from bytes:" + bytes);
        byte[] tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < BFT.messages.MessageTags.uint32Size; i++, offset++)
            tmp[i] = bytes[offset];
        startSeqNum = util.UnsignedTypes.bytesToLong(tmp);
        //System.out.println("\tCheckPoints from bytes: startSeqNUm " + startSeqNum);
        // read the next sequence number
        for (int i = 0; i < BFT.messages.MessageTags.uint32Size; i++, offset++)
            tmp[i] = bytes[offset];
        nextSeqNum = util.UnsignedTypes.bytesToLong(tmp);
        //System.out.println("\tCheckPoints from bytes: nextSeqNum" + nextSeqNum);
        // read the size of the replies
        //tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < BFT.messages.MessageTags.uint32Size; i++, offset++)
            tmp[i] = bytes[offset];
        int num = (int) util.UnsignedTypes.bytesToLong(tmp);
        //System.out.println("\tCheckPoints from bytes: replies number " + num);
        // and the replies itself
        replies = new ArrayList<Reply>(num);

        for (int i = 0; i < num; i++) {
            for (int j = 0; j < BFT.messages.MessageTags.uint32Size; j++, offset++) {
                tmp[j] = bytes[offset];
            }
            int size = (int) util.UnsignedTypes.bytesToLong(tmp);
//            System.out.println("\tCheckPoints from bytes: reply size " + size
//                    + " bytes:"+ tmp);
//
            byte[] tmp2 = new byte[size];
            for (int k = 0; k < size; k++, offset++) {
                tmp2[k] = bytes[offset];
            }
            Reply reply = new Reply(tmp2);
            replies.add(reply);
        }

        if (offset != bytes.length) {
            System.out.println("offset: " + offset);
            System.out.println("bytes : " + bytes.length);
            BFT.Debug.kill("horrible mismatch in loading a reply cache from bytes");
        }

    }

    byte[] bytes = null;

    public synchronized byte[] getBytes() {
        if (bytes != null) {
            return bytes;
        }
        int sum = 0;
        //System.out.println("\tCheckPoints: sum size " + sum);
        for (int i = 0; i < replies.size(); i++) {
            sum += replies.get(i).getBytes().length + UnsignedTypes.uint32Size;
            //System.out.println("\tCheckPoints: sum size " + sum);
        }
        sum += UnsignedTypes.uint32Size * 3;
        bytes = new byte[sum];

        int offset = 0;
        // copy the start seqNum
        byte[] tmp = UnsignedTypes.longToBytes(startSeqNum);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
        //System.out.println("\tCheckPoints: startSeq" + startSeqNum);
        // copy the next seqNum
        tmp = UnsignedTypes.longToBytes(nextSeqNum);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
        //System.out.println("\tCheckPoints: nextSeq" + nextSeqNum);
        // copy the number of replies
        tmp = UnsignedTypes.longToBytes(new Long(replies.size()));
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
        //System.out.println("\tCheckPoints: replies num " + replies.size());
        Reply reply;
        for (int i = 0; i < replies.size(); i++) {
            reply = replies.get(i);
            // copy the size of reply
            tmp = UnsignedTypes.longToBytes(new Long(reply.getBytes().length));
            //System.out.println("\tCheckPoints: reply size " + reply.getBytes().length
            //        + "bytes:" + tmp);
            for (int j = 0; j < tmp.length; j++, offset++) {
                bytes[offset] = tmp[j];
            }
            tmp = reply.getBytes();
            for (int j = 0; j < tmp.length; j++, offset++) {
                bytes[offset] = tmp[j];
            }
        }
        //System.out.println("\tCheckPoints: sum size " + bytes.length);
        //System.out.println("\tCheckPoints: " + bytes);
        //markStable();
        return bytes;
    }

    public long getSize() {
        return nextSeqNum - startSeqNum;
    }

    public void addReply(Reply rep, long seqno) {
        //System.out.println("\tCP:add reply " + seqno + ": nextSeqNum " + nextSeqNum);
        if (seqno != nextSeqNum) {
            return;
        }
        nextSeqNum++;
        replies.add(rep);
    }

    public Reply fetchReply(long seq) {
        long index = seq - startSeqNum;
        if (seq > nextSeqNum || seq < startSeqNum || seq - startSeqNum >= replies.size()) {
            System.out.println("CP: reply not exists!");
            return null;
        }
        return replies.get((int) index);
    }

    public long getStartSeqNum() {
        return this.startSeqNum;
    }

    public long getNextSeqNum() {
        return this.nextSeqNum;
    }

    public void setStartSeqNum(long seq) {
        this.startSeqNum = seq;
    }

    public void setNextSeqNum(long seq) {
        this.nextSeqNum = seq;
    }
}
