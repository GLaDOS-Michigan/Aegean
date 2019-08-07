// $Id: SignedRequestCore.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * Request message sent from the client to the order node.
 **/
public class SignedRequestCore extends SignedMessage implements RequestCore {


    /**
     * Construct that accepts specific message fields.  This
     * constructor builds up the message byte representation starting
     * from where VerifiedMessageBase leaves off.
     **/
    public SignedRequestCore(Parameters param, long client, long subId, long sequence, long GC, byte[] com) {
        this(param, new Entry(param, client, subId, sequence, GC, com));
// 	seqNo = sequence;
// 	command = com;

// 	// now lets get the bytes
// 	byte[] bytes = getBytes();

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

    public SignedRequestCore(Parameters param, long client, long subId, long sequence, byte[] com) {
        this(param, new Entry(param, client, subId, sequence, com));
    }

    public SignedRequestCore(Parameters param, Entry ent) {
        super(param, tag(), computeSize(ent), ent.getClient());
        entry = ent;
        // now lets get the bytes
        byte[] bytes = getBytes();
        int offset = getOffset();
        byte[] tmp = entry.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        //this.entry = entry;
    }

    /**
     * Constructor accepting a byte representation of the message.
     * Parses the byte representation to populate the class fields.
     **/
    public SignedRequestCore(byte[] bytes, Parameters param) {
        super(bytes, param);
        if (getTag() != MessageTags.SignedRequestCore) {
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

//     private long seqNo;
//     private byte[] command;

    /* (non-Javadoc)
     * @see BFT.messages.RequestCore#getSendingClient()
	 */
    public int getSendingClient() {
        return (int) entry.getClient();
    }


    public Entry getEntry() {
        return entry;
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

    /**
     * Total size of the request message based on the definition in
     * request.hh and verifiable_msg.hh
     **/
    static private int computeSize(Entry entry) {
        return entry.getSize();
    }


    public static int tag() {
        return MessageTags.SignedRequestCore;
    }


    public boolean equals(SignedRequestCore r) {
        boolean res = super.equals(r)
                && entry.equals(r.entry);
        return res;
    }

    public String toString() {
        String com = "";
        for (int i = 0; i < 8 && i < getCommand().length; i++) {
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
        SignedRequestCore vmb = new SignedRequestCore(param, 1, 0, 0, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        SignedRequestCore vmb2 = new SignedRequestCore(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        for (int i = 0; i < 8; i++)
            tmp[i] = (byte) (tmp[i] * tmp[i]);

        vmb = new SignedRequestCore(param, 134, 0, 8, tmp);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new SignedRequestCore(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());
        //System.out.println("old = new: "+(vmb2.toString().equals(vmb.toString())));

        //System.out.println("old.equals(new): "+vmb.equals(vmb2));
    }
}
