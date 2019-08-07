// $Id: ExecViewChangeMessage.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * @author yangwang
 */
public class ExecViewChangeMessage extends MacArrayMessage {

    private long viewNo;

    public ExecViewChangeMessage(Parameters param, long view, int sendingReplica) {
        super(param, MessageTags.ExecViewChange,
                computeSize(view),
                sendingReplica,
                param.getFilterCount());
        this.viewNo = view;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;
    }


    public ExecViewChangeMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.ExecViewChange) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public long getViewNo() {
        return this.viewNo;
    }


    private static int computeSize(long view) {
        int size = MessageTags.uint32Size;
        return size;
    }

    public boolean equals(ExecViewChangeMessage nb) {
        return super.equals(nb) && viewNo == nb.viewNo;
    }


    public String toString() {
        return "<VerifyMessage, " + getSender() + ", " + getViewNo() + ">";
    }

    @Override
    public boolean matches(VerifiedMessageBase vmb) {
        ExecViewChangeMessage target = (ExecViewChangeMessage) vmb;

        boolean ret = this.viewNo == target.viewNo;
        return ret;
    }

}
