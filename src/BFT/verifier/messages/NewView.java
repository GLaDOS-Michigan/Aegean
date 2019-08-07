// $Id: NewView.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.MacArrayMessage;
import BFT.util.UnsignedTypes;

/**

 **/
public class NewView extends MacArrayMessage {


    /**
     * k is the number of non-null entires in the chosenvcs array
     **/
    public NewView(Parameters param, long view, DigestId[] chosenvcs,
                   HistoryStateSeqno newstate, long sendingReplica) {
        super(param, MessageTags.NewView, computeSize(param, chosenvcs, newstate),
                sendingReplica,
                param.getVerifierCount());
        viewNo = view;
        chosenVCs = chosenvcs;
        newState = newstate;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp = UnsignedTypes.longToBytes(viewNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        tmp = UnsignedTypes.intToBytes(chosenvcs.length);
        System.arraycopy(tmp, 0, bytes, offset, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;

        // place the view change messages
        for (int j = 0; j < chosenVCs.length; j++) {
            // place the order index of the replica
            tmp = chosenVCs[j].getBytes();
            System.arraycopy(tmp, 0, bytes, offset, tmp.length);
            offset += tmp.length;
        }

        tmp = newState.getBytes();
        System.arraycopy(tmp, 0, bytes, offset, tmp.length);
        offset += tmp.length;

    }


    /**
     * k is the number of non-null entries in the vc array.
     * n is the total size of said array
     **/
    public NewView(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.NewView) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        tmp = new byte[MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        viewNo = UnsignedTypes.bytesToLong(tmp);

        tmp = new byte[MessageTags.uint16Size];
        System.arraycopy(bits, offset, tmp, 0, MessageTags.uint16Size);
        int length = UnsignedTypes.bytesToInt(tmp);
        offset += MessageTags.uint16Size;

        // read the vc hashes
        chosenVCs = new DigestId[length];
        byte[] tmpDigestBytes = new byte[DigestId.size(param)];
        for (int j = 0; j < length; j++) {
            System.arraycopy(bits, offset, tmpDigestBytes, 0, DigestId.size(param));
            chosenVCs[j] = new DigestId(tmpDigestBytes, param);
            offset += DigestId.size(param);
        }

        tmpDigestBytes = new byte[HistoryStateSeqno.size(param)];
        System.arraycopy(bits, offset, tmpDigestBytes, 0, HistoryStateSeqno.size(param));
        newState = new HistoryStateSeqno(tmpDigestBytes, param);
        offset += HistoryStateSeqno.size(param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte array");
        }
    }


    public long getSendingReplica() {
        return getSender();
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected DigestId[] chosenVCs;

    public DigestId[] getChosenVCs() {
        return chosenVCs;
    }

    protected HistoryStateSeqno newState;

    public HistoryStateSeqno getNewState() {
        return newState;
    }


    public boolean equals(NewView nb) {
        boolean res = super.equals(nb) && viewNo == nb.viewNo && newState.equals(nb.getNewState());
        for (int i = 0; i < getChosenVCs().length && res; i++) {
            if (chosenVCs[i] == null)
                res = res && nb.chosenVCs[i] == null;
            else
                res = res && chosenVCs[i].equals(nb.chosenVCs[i]);
        }
        return res;

    }

    /**
     * computes the size of the bits specific to NewView
     **/
    private static int computeSize(Parameters param, DigestId[] vcs, HistoryStateSeqno newState) {
        int size = MessageTags.uint32Size; // viewNo
        size += MessageTags.uint16Size;     // array length
        size += (vcs.length) * DigestId.size(param); // array of VC digests
        size += HistoryStateSeqno.size(param); // newState
        return size;
    }


    public String toString() {
        String res = "<NEW-VIEW, " + super.toString() + ", view=" + viewNo;
        res = res + ", " + newState + ">";
        //for (int i = 0; i < chosenVCs.length; i++)
        //    res = res +", "+i+":"+(chosenVCs[i]!=null);
        //res = res +">";
        return res;
    }

    public String dumpVCs() {
        String str = "";
        for (int i = 0; i < getChosenVCs().length; i++) {
            str += getChosenVCs()[i] + "\n";
        }
        return str;
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        Digest tmpD = new Digest(param, tmp);

        DigestId[] vcs = new DigestId[3];
        vcs[0] = new DigestId(param, tmpD, 1);
        vcs[1] = new DigestId(param, tmpD, 1);
        vcs[2] = new DigestId(param, tmpD, 1);

        HistoryStateSeqno ns = new HistoryStateSeqno(param, tmpD, tmpD, 1234);


        NewView vmb = new NewView(param, 123, vcs, ns, 1);
        byte[] firstBytes = vmb.getBytes();
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        NewView vmb2 = new NewView(vmb.getBytes(), param);
        byte[] secondBytes = vmb2.getBytes();
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        /*
        vmb = new NewView(42, cv, 2, 1);
		//System.out.println("\ninitial: "+vmb.toString());
		UnsignedTypes.printBytes(vmb.getBytes());
		vmb2 = new NewView(vmb.getBytes(),1, 4);
		//System.out.println("\nsecondary: "+vmb2.toString());
		UnsignedTypes.printBytes(vmb2.getBytes());
		*/
        //System.out.println("\nold = new: "+vmb.equals(vmb2));

        if (firstBytes.length != secondBytes.length) {
            System.out.println("Messages have different length");
        }
        boolean res = true;
        int i = 0;
        for (; i < firstBytes.length; i++) {
            if (firstBytes[i] != secondBytes[i]) {
                res = false;
                break;
            }
        }
        System.out.println();
        if (res) {
            System.out.println("Identical messages");
        } else {
            System.out.println("*******************************");
            System.out.println("Messages differ at position " + i);
            System.out.println("*******************************");
        }
    }

}