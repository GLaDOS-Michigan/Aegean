// $Id: NewView.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.MacArrayMessage;
import util.UnsignedTypes;

/**

 **/
public class NewView extends MacArrayMessage {


    public NewView(Parameters param, long view, ViewChangeCore[] chosenvcs,
                   long sendingReplica, int numberOfChosenVcs) {
        super(param, MessageTags.NewView, computeSize(chosenvcs, numberOfChosenVcs),
                sendingReplica, param.getOrderCount());
        System.out.println("adjust message size and byte constructors");
        System.out.println("place vcs directly in the new view message" +
                " and verify them each time.  skip the " +
                "vcmissing step entirely");
    }

    /**
     * k is the number of non-null entires in the chosenvcs array
     **/
    public NewView(Parameters param, long view, Digest[] chosenvcs, long sendingReplica, int k) {
        super(param, MessageTags.NewView, computeSize(param, chosenvcs, k), sendingReplica, param.getOrderCount());
        viewNo = view;
        chosenVCs = chosenvcs;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the view change messages
        for (int j = 0; j < chosenVCs.length; j++) {
            if (chosenVCs[j] != null) {
                // place the order index of the replica
                UnsignedTypes.longToBytes(j, bytes, offset);
                offset += UnsignedTypes.uint32Size;
                // place the VC digest
                tmp = chosenVCs[j].getBytes();
                for (int i = 0; i < tmp.length; i++, offset++) {
                    bytes[offset] = tmp[i];
                }
            }
        }
    }


    /**
     * k is the number of non-null entries in the vc array.
     * n is the total size of said array
     **/
    public NewView(byte[] bits, int k, int n, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.NewView)
            throw new RuntimeException("invalid message Tag: " + getTag());

        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;


        // read the vc hashes
        chosenVCs = new Digest[n];
        byte[] tmpDigestBytes = new byte[Digest.size(param)];
        for (int j = 0; j < k; j++) {
            // get the replica index

            int loc = (int) (UnsignedTypes.bytesToLong(bits, offset));
            offset += UnsignedTypes.uint32Size;
            // get the vc hash
            for (int i = 0; i < tmpDigestBytes.length; i++, offset++) {
                tmpDigestBytes[i] = bits[offset];
            }
            chosenVCs[loc] = Digest.fromBytes(param, tmpDigestBytes);
        }
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

    protected Digest[] chosenVCs;

    public Digest[] getChosenVCs() {
        return chosenVCs;
    }


    public boolean equals(NewView nb) {
        boolean res = super.equals(nb) && viewNo == nb.viewNo;
        for (int i = 0; i < getChosenVCs().length && res; i++) {
            if (chosenVCs[i] == null) {
                res = res && nb.chosenVCs[i] == null;
            } else {
                res = res && chosenVCs[i].equals(nb.chosenVCs[i]);
            }
        }
        return res;
    }

    /**
     * computes the size of the bits specific to NewView
     **/
    private static int computeSize(Parameters param, Digest[] vcs, int k) {
        int size = UnsignedTypes.uint32Size;
        size += k * (Digest.size(param) + UnsignedTypes.uint32Size);
        return size;
    }

    private static int computeSize(ViewChangeCore[] vcs, int k) {
        int size = UnsignedTypes.uint32Size;
        for (int i = 0; i < vcs.length; i++, k--) {
            if (vcs[i] != null) {
                size += vcs[i].getBytes().length;
            }
        }
        if (k != 0) {
            BFT.Debug.kill("need " + k + " more VCS to make a valid new view");
        }
        return size;
    }


    public String toString() {
        String res = "<NEW-VIEW, " + super.toString() + ", view=" + viewNo;
        for (int i = 0; i < chosenVCs.length; i++) {
            res = res + ", " + i + ":" + (chosenVCs[i] != null);
        }
        res = res + ">";
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
        Digest[] cv = new Digest[4];
        for (int i = 0; i < cv.length; i++)
            cv[i] = new Digest(param, tmp);

        cv[2] = null;

        NewView vmb = new NewView(param, 123, cv, 1, 1);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        NewView vmb2 =
                new NewView(vmb.getBytes(), 1, 4, param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new NewView(param, 42, cv, 2, 1);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new NewView(vmb.getBytes(), 1, 4, param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

}