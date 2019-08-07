// $Id: AppState.java 2267 2009-01-20 06:55:52Z aclement $

package BFT.exec.messages;

import BFT.messages.MacMessage;
import BFT.util.UnsignedTypes;

public class AppStateMessage extends MacMessage {
    public AppStateMessage(long startSeqNo, long endSeqNo, byte[] st, long sender) {
        super(MessageTags.AppState,
                computeSize(startSeqNo, endSeqNo, st), sender);

        this.startSeqNo = startSeqNo;
        this.endSeqNo = endSeqNo;
        state = st;

        // now lets get the bytes
        byte[] bytes = getBytes();

        // copy the sequence no
        byte[] tmp = UnsignedTypes.longToBytes(startSeqNo);
        int offset = getOffset();
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];

        tmp = UnsignedTypes.longToBytes(endSeqNo);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];


        // copy the size of the token
        //System.out.println("size of state: "+state.length);
        tmp = UnsignedTypes.longToBytes(state.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];
        // copy the token
        for (int i = 0; i < state.length; i++, offset++)
            bytes[offset] = state[i];
    }


    public AppStateMessage(byte[] bytes) {
        super(bytes);
        if (getTag() != MessageTags.AppState)
            throw new RuntimeException("invalid message Tag: " + getTag());

        // pull the length of the token
        byte[] tmp = new byte[BFT.messages.MessageTags.uint32Size];
        int offset = getOffset();
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        startSeqNo = UnsignedTypes.bytesToLong(tmp);

        tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        endSeqNo = UnsignedTypes.bytesToLong(tmp);

        // pull the length of the state
        tmp = new byte[BFT.messages.MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        int size = (int) (UnsignedTypes.bytesToLong(tmp));
        //System.out.println("State has size: "+size);
        state = new byte[size];
        // pull the state itself
        for (int i = 0; i < state.length; i++, offset++)
            state[i] = bytes[offset];

        if (offset != bytes.length - getAuthenticationSize()) {
            System.out.println("offset: " + offset);
            System.out.println("bytes.length: " + bytes.length);
            System.out.println("authenticationsize: " + getAuthenticationSize());
            BFT.Debug.kill(new RuntimeException("Invalid byte input " + offset + " != " +
                    (bytes.length - getAuthenticationSize())));
        }
    }


    protected long startSeqNo;
    protected long endSeqNo;
    protected byte[] state;

    public long getSendingReplica() {
        return getSender();
    }

    public long getStartSeqNo() {
        return startSeqNo;
    }

    public long getEndSeqNo() {
        return endSeqNo;
    }

    public byte[] getState() {
        return state;
    }

    private static int computeSize(long startSeqNo, long endSeqNo, byte[] st) {
        return MessageTags.uint32Size + MessageTags.uint32Size + MessageTags.uint32Size + st.length;
    }


    public String toString() {
        return "<APP-sSTATE, " + super.toString() + ", token: " + startSeqNo + "->" + endSeqNo + ">";
    }

}
