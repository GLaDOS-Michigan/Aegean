// $Id: RelayViewChange.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.MacBytes;
import BFT.messages.MacMessage;

/**

 **/
public class RelayViewChange extends MacMessage {


    public RelayViewChange(ViewChange vc, MacBytes[] m, int sendingReplica) {
        super(MessageTags.RelayViewChange, computeSize(vc, m), sendingReplica);

        viewchange = vc;
        macs = m;

        int offset = getOffset();
        byte[] bytes = getBytes();

        byte[] tmp;
        // put the mac array down
        for (int i = 0; i < macs.length; i++) {
            tmp = macs[i].getBytes();
            for (int j = 0; j < tmp.length; j++, offset++)
                bytes[offset] = tmp[j];
        }

        // put down the view change
        tmp = vc.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        if (offset != getBytes().length - getAuthenticationSize()) {
            Debug.kill("error in writing bytes down");
        }
    }

    public RelayViewChange(byte[] bits, int macArraySize, Parameters param) {
        super(bits);
        int offset = getOffset();
        byte[] tmp;

        macs = new MacBytes[macArraySize];

        for (int i = 0; i < macs.length; i++) {
            tmp = new byte[MacBytes.size()];
            for (int j = 0; j < macs.length; j++, offset++) {
                tmp[j] = bits[offset];
            }
            macs[i] = new MacBytes(tmp);
        }

        tmp = new byte[getBytes().length - getAuthenticationSize() - offset];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        viewchange = new ViewChange(tmp, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected ViewChange viewchange;

    public ViewChange getViewChange() {
        return viewchange;
    }

    protected MacBytes[] macs;

    public MacBytes[] getMacs() {
        return macs;
    }

    public long getSendingReplica() {
        return getSender();
    }


    /**
     * computes the size of the bits specific to RelayViewChange
     **/
    private static int computeSize(ViewChange vc, MacBytes[] macs) {
        int size = 0;
        for (int i = 0; i < macs.length; i++) {
            size += MacBytes.size();
        }
        size += vc.getTotalSize();
        return size;
    }


}