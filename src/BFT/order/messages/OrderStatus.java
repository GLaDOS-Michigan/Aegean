// $Id: OrderStatus.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

/**

 **/
public class OrderStatus extends MacArrayMessage {


    public OrderStatus(Parameters param, long view, long c, long p, long pp, int sendingReplica) {
        super(param, MessageTags.OrderStatus, computeSize(), sendingReplica, param.getExecutionCount());
        viewNo = view;
        comSeqNo = c;
        prepSeqNo = p;
        ppSeqNo = pp;

        if (c > p || p > pp || c > pp) {
            BFT.Debug.kill(c + " " + p + " " + pp + " should be non-decreasing");
        }

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the committed  sequence number
        UnsignedTypes.longToBytes(comSeqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the prepared sequencenumber
        UnsignedTypes.longToBytes(prepSeqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // place the preprepared sequence number
        UnsignedTypes.longToBytes(ppSeqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }

    public OrderStatus(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.OrderStatus) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();

        // read the view number;

        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the c sequence number
        comSeqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        // read the psequence number
        prepSeqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;
        // read the pp sequence number
        ppSeqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long comSeqNo;

    public long getLastCommitted() {
        return comSeqNo;
    }

    protected long prepSeqNo;

    public long getLastPrepared() {
        return prepSeqNo;
    }

    protected long ppSeqNo;

    public long getLastPrePrepared() {
        return ppSeqNo;
    }

    public long getSendingReplica() {
        return getSender();
    }

    /**
     * computes the size of the bits specific to Commit
     **/
    private static int computeSize() {
        int size = UnsignedTypes.uint32Size + UnsignedTypes.uint32Size + UnsignedTypes.uint32Size + UnsignedTypes.uint32Size;
        return size;
    }
}