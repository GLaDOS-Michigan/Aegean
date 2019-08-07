// $Id: ViewChange.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.HistoryDigest;
import BFT.messages.MacArrayMessage;
import BFT.util.UnsignedTypes;


/**
 **/
public class ViewChange extends MacArrayMessage {


    /**

     **/
    public ViewChange(Parameters param, long view, HistoryStateSeqno maxP,
                      HistoryStateSeqno[] PPs,
                      int sendingReplica) {
        super(param, MessageTags.ViewChange,
                computeSize(param, maxP, PPs),
                sendingReplica, param.getVerifierCount());
        //Parameters.getVerifierCount());

        //System.out.println("Created a byte[] of length "+getBytes().length);
        viewNo = view;
        maxPrepared = maxP;
        preprepares = PPs;

        int offset = getOffset();
        //System.out.println("Creating VC: initial offset="+offset);
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp = UnsignedTypes.longToBytes(viewNo);
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        //System.out.println("Creating VC: offset after view ="+offset);

        int HSSsize = HistoryStateSeqno.size(parameters);
        System.arraycopy(maxP.getBytes(), 0, bytes, offset, HSSsize);
        offset += HSSsize;

        //System.out.println("Creating VC: offset after maxP ="+offset);

        tmp = UnsignedTypes.intToBytes(PPs.length);
        System.arraycopy(tmp, 0, bytes, offset, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;

        //System.out.println("Creating VC: offset after PPs.length ="+offset);

        for (int i = 0; i < PPs.length; i++) {
            System.arraycopy(PPs[i].getBytes(), 0, bytes, offset, HSSsize);
            offset += HSSsize;
        }

        //System.out.println("Creating VC: offset after PPs ="+offset);

        //System.out.println("Auth size: "+getAuthenticationSize());

    }

    public ViewChange(byte[] bits, Parameters param) {
        super(bits, param);
        //System.out.println("Going to construct VC off of "+bits.length+" bytes");
        if (getTag() != MessageTags.ViewChange) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }

        int offset = getOffset();
        byte[] tmp;

        //System.out.println("Initial offset = "+offset);
        // read the view number;
        tmp = new byte[MessageTags.uint32Size];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        viewNo = UnsignedTypes.bytesToLong(tmp);

        //System.out.println("Offset after view = "+offset);
        int HSSsize = HistoryStateSeqno.size(parameters);
        tmp = new byte[HSSsize];
        System.arraycopy(bits, offset, tmp, 0, HSSsize);
        maxPrepared = new HistoryStateSeqno(tmp, parameters);
        offset += HSSsize;

        //System.out.println("Offset after maxP = "+offset);

        byte[] tmp2 = new byte[MessageTags.uint16Size];
        System.arraycopy(bits, offset, tmp2, 0, MessageTags.uint16Size);
        int length = UnsignedTypes.bytesToInt(tmp2);
        offset += MessageTags.uint16Size;

        //System.out.println("Offset after PPs.length = "+offset);

        preprepares = new HistoryStateSeqno[length];
        for (int i = 0; i < length; i++) {
            System.arraycopy(bits, offset, tmp, 0, HSSsize);
            preprepares[i] = new HistoryStateSeqno(tmp, parameters);
            offset += HSSsize;
        }
        //System.out.println("Offset after PPs = "+offset);

        //System.out.println("offset="+offset+" getBytes().length="+getBytes().length+" authSize="+getAuthenticationSize());
        // need error checking to assert that cp <= p <= pp
        // offset is at the end of the message

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected HistoryStateSeqno maxPrepared;

    public HistoryStateSeqno getMaxPrepared() {
        return maxPrepared;
    }

    protected HistoryStateSeqno[] preprepares;

    public HistoryStateSeqno[] getPreprepares() {
        return preprepares;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(ViewChange nb) {
        if (preprepares.length != nb.preprepares.length) {
            return false;
        }

        boolean res = super.equals(nb) && viewNo == nb.viewNo &&
                maxPrepared.equals(nb.maxPrepared);

        for (int i = 0; i < preprepares.length; i++)
            res = res && preprepares[i].equals(nb.preprepares[i]);

        return res;
    }

    /**
     returns the index of the largest sequence number such that vc
     and this are compatible with each other

     public long maxCompatibleSequenceNumber(ViewChange vc){
     long base = vc.getCommittedCPSeqNo();
     long myBase = this.getCommittedCPSeqNo();
     // if base > mybase, then flip sides
     if (base > myBase)
     return vc.maxCompatibleSequenceNumber(this);
     // if vc.pp < mybase, then mybase-1 is max compatible
     if (vc.getPPSeqNo() < getCommittedCPSeqNo())
     return myBase -1;

     // invariant:  mybase >= base
     // invariant:  mybase <= vc.pp
     int offset = (int) (myBase - base);
     long result = myBase-1;
     for (int i = 0;
     i < batchHashes.length &&
     offset < vc.getBatchHashes().length ;
     i++, offset++){
     if (batchHashes[i].equals(vc.getBatchHashes()[offset]) &&
     histories[i].equals(vc.getHistories()[offset]))
     result++;
     }
     return result;
     }**/

    /**
     * returns true if vc contains a cp that is consistent with the
     * local committed cp

     public boolean matchesCommittedCP(ViewChange vc){
     return (vc.getCommittedCPSeqNo() == getCommittedCPSeqNo()
     && vc.getCommittedCPHash().equals(getCommittedCPHash()))
     || (vc.getStableCPSeqNo() == getCommittedCPSeqNo()
     && vc.getStableCPHash().equals(getCommittedCPHash()));
     }**/

    /**
     * computes the size of the bits specific to ViewChange
     **/
    private static int computeSize(Parameters param, HistoryStateSeqno maxP, HistoryStateSeqno[] PPs) {
        return MessageTags.uint32Size +      //viewNo
                HistoryStateSeqno.size(param) +    //maxP
                MessageTags.uint16Size +        //PPs.length
                (PPs.length) * (HistoryStateSeqno.size(param));    //PPs
    }

    @Override
    public String toString() {
        String pps = "<";
        for (int i = 0; i < preprepares.length; i++) {
            pps = pps + preprepares[i].getSeqno();
            if (i != preprepares.length - 1) {
                pps = pps + ",";
            }
        }
        pps = pps + ">";
        return "<VC, " + super.toString() + ", v:" + viewNo +
                ", maxP:" + maxPrepared.getSeqno() +
                ", PPs: " + pps + ">";
    }

    public static void main(String args[]) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        long seqNo = 532;
        HistoryStateSeqno maxP = new HistoryStateSeqno(param, d, d, seqNo);

        HistoryStateSeqno[] pps = new HistoryStateSeqno[2];
        for (int i = 0; i < 2; i++) {
            pps[i] = new HistoryStateSeqno(param, d, d, seqNo + i + 1);
        }

        ViewChange vmb = new ViewChange(param, 2, maxP, pps, 1);
        byte[] firstBytes = vmb.getBytes();
        System.out.println("initial: " + vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        ViewChange vmb2 = new ViewChange(vmb.getBytes(), param);
        byte[] secondBytes = vmb2.getBytes();
        System.out.println("\nsecondary: " + vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

	        /*
        vmb = new ViewChange(134,22,22,24,28, d,d, ppHash,new HistoryDigest[0], 235);
		//System.out.println("\ninitial: "+vmb.toString());
		UnsignedTypes.printBytes(vmb.getBytes());
		vmb2 = new ViewChange(vmb.getBytes());
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