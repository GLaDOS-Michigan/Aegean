// $Id: FilteredRequestCore.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * Request message sent from the client to the order node.
 **/
public class FilteredRequestCore extends MacSignatureMessage implements RequestCore {


    /**
     * Construct that accepts specific message fields.  This
     * constructor builds up the message byte representation starting
     * from where VerifiedMessageBase leaves off.
     **/
    public FilteredRequestCore(Parameters param, long client, long subId, long sequence,
                               byte[] com) {
        this(param, new Entry(param, client, subId, sequence, com));
    }


    int sendingRep;

    public void setSendingReplica(int i) {
        sendingRep = i;
    }

    public int getSendingReplica() {
        return sendingRep;
    }

    public FilteredRequestCore(Parameters param, Entry entry) {
        super(param, tag(), computeSize(entry), param.getFilterCount(),
                param.useVerifier ? param.getExecutionCount() : param.getOrderCount());

        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        byte[] tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++)
            bytes[offset] = tmp[i];

        this.entry = entry;

        // 	// copy the sequence number over
        // 	byte[] tmp = UnsignedTypes.longToBytes(sequence);
        // 	int offset = getOffset();
        // 	for (int i = 0; i < tmp.length; i++, offset++)
        // 	    bytes[offset] = tmp[i];

        // 	// copy the command size over
        // 	tmp = UnsignedTypes.longToBytes(com.length);
        // 	for (int i = 0; i < tmp.length; i++, offset++)
        // 	    bytes[offset] = tmp[i];

        // 	// copy the command itself over
        // 	for (int i = 0; i < com.length; i++, offset++)
        // 	    bytes[offset] = com[i];
    }

    /**
     * Constructor accepting a byte representation of the message.
     * Parses the byte representation to populate the class fields.
     **/
    public FilteredRequestCore(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.FilteredRequestCore) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();

        Entry tmp = Entry.fromBytes(param, bytes, offset);
        entry = tmp;

        offset += entry.getSize();

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    private Entry entry;

    /* (non-Javadoc)
     * @see BFT.messages.RequestCore#getSendingClient()
	 */
    public int getSendingClient() {
        return (int) entry.getClient();
    }

    public int getSender() {
        return getSendingClient();
    }

    /* (non-Javadoc)
	 * @see BFT.messages.RequestCore#getSubId()
	 */
    public int getSubId() {
        return (int) entry.getSubId();
    }

    /* (non-Javadoc)
	 * @see BFT.messages.RequestCore#getRequestId()
	 */
    public long getRequestId() {
        return entry.getRequestId();
    }

    public long getGCId() {
        return entry.getGCId();
    }

    /* (non-Javadoc)
	 * @see BFT.messages.RequestCore#getCommand()
	 */
    public byte[] getCommand() {
        return entry.getCommand();
    }

    public void setCommand(byte[] com) {
        entry.setCommand(com);
    }

    public Entry getEntry() {
        return entry;
    }

    /**
     * Total size of the request message based on the definition in
     * request.hh and verifiable_msg.hh
     **/
    static private int computeSize(Entry entry) {
        return entry.getSize();
    }

    public static int tag() {
        return MessageTags.FilteredRequestCore;
    }


    public boolean equals(FilteredRequestCore r) {
        boolean res = super.equals(r) && matches(r);
        return res;
    }

    public boolean matches(VerifiedMessageBase vmb) {
        boolean res = vmb.getTag() == getTag();
        if (!res) {
            return res;
        }
        FilteredRequestCore rc = (FilteredRequestCore) vmb;
        res = res && entry.equals(rc.getEntry());
        return res;
    }

    public String toString() {
        String com = "";
        for (int i = 0; getCommand() != null && i < 8 && i < getCommand().length; i++) {
            com += getCommand()[i] + ",";
        }
        return "< REQ, " + super.toString() + ", reqId:" + getRequestId() + ", command:" + com + ">";
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        FilteredRequestCore vmb = new FilteredRequestCore(param, 1, 0, 0, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        FilteredRequestCore vmb2 = new FilteredRequestCore(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) (tmp[i] * tmp[i]);
        }

        vmb = new FilteredRequestCore(param, 134, 0, 8, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new FilteredRequestCore(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());
        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));

        //System.out.println("old.equals(new): "+vmb.equals(vmb2));
    }
}
