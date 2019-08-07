// $Id: PrePrepare.java 728 2011-09-11 23:44:18Z aclement $

package BFT.order.messages;

import BFT.Parameters;
import BFT.messages.*;
import util.UnsignedTypes;

/**

 **/
public class PrePrepare extends MacArrayMessage {

    public PrePrepare(Parameters param, long view, long seq, HistoryDigest hist,
                      RequestBatch batch, NonDeterminism non,
                      Digest cp, int sendingReplica) {
        super(param, MessageTags.PrePrepare,
                computeSize(param, view, seq, hist, batch, non, cp),
                sendingReplica,
                param.getOrderCount());

        viewNo = view;
        seqNo = seq;
        history = hist;
        reqBatch = batch;
        nondet = non;
        cpHash = cp;

        cleanCount = 0;

        int offset = getOffset();
        byte[] bytes = getBytes();
        // place the view number
        byte[] tmp;
        UnsignedTypes.longToBytes(viewNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the sequence number
        UnsignedTypes.longToBytes(seqNo, bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the history
        tmp = history.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the nondeterminism
        // size first
        UnsignedTypes.longToBytes(nondet.getSize(), bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // now the nondet bytes
        tmp = non.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // checkpoint hash
        tmp = cpHash.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the size of the batch
        UnsignedTypes.longToBytes(batch.getSize(), bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the number of entries in the batch
        UnsignedTypes.intToBytes(batch.getEntries().length, bytes, offset);
        offset += UnsignedTypes.uint16Size;


        // place the batch bytes
        tmp = batch.getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public PrePrepare(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.PrePrepare) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        byte[] tmp;

        // read the view number;

        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the sequence number
        seqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the history
        tmp = new byte[HistoryDigest.size(param)];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        history = HistoryDigest.fromBytes(tmp, param);

        // read the non det size
        int nondetSize = (int) (UnsignedTypes.bytesToLong(bits, offset));
        offset += UnsignedTypes.uint32Size;
        // read the nondeterminism
        tmp = new byte[nondetSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        nondet = new NonDeterminism(tmp);

        // read the checkpoint hash
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        cpHash = Digest.fromBytes(param, tmp);

        // read the batch size
        int batchSize = (int) (UnsignedTypes.bytesToLong(bits, offset));
        offset += UnsignedTypes.uint32Size;
        // read the number of entries in the batch
        int count = (int) (UnsignedTypes.bytesToInt(bits, offset));
        offset += UnsignedTypes.uint16Size;

        // read the batch bytes
        tmp = new byte[batchSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        reqBatch = new RequestBatch(tmp, count, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    protected long viewNo;

    public long getView() {
        return viewNo;
    }

    protected long seqNo;

    public long getSeqNo() {
        return seqNo;
    }

    protected HistoryDigest history;

    public HistoryDigest getHistory() {
        return history;
    }

    protected RequestBatch reqBatch;

    public RequestBatch getRequestBatch() {
        return reqBatch;
    }

    protected NonDeterminism nondet;

    public NonDeterminism getNonDeterminism() {
        return nondet;
    }

    protected Digest cpHash;

    public Digest getCPHash() {
        return cpHash;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(PrePrepare nb) {
        return super.equals(nb) && viewNo == nb.viewNo && seqNo == nb.seqNo &&
                history.equals(nb.history) && cpHash.equals(nb.cpHash) &&
                nondet.equals(nb.nondet) && reqBatch.equals(nb.reqBatch);
    }

    /**
     * computes the size of the bits specific to PrePrepare
     **/
    private static int computeSize(Parameters param, long view, long seq, HistoryDigest h,
                                   RequestBatch batch, NonDeterminism non, Digest cp) {
        int size = UnsignedTypes.uint32Size // view number, 4 bytes
                + UnsignedTypes.uint32Size // sequence number, 4 bytes
                + HistoryDigest.size(param) //history
                + UnsignedTypes.uint32Size // non det size
                + non.getSize() // non det itself
                + param.digestLength // Digest.size() //  cp hash
                + UnsignedTypes.uint32Size // bytes in the batch
                + UnsignedTypes.uint16Size // entries in batch
                + batch.getSize(); // batch
        return size;
    }


    public String toString() {
        return "<PP, view=" + getView() + ", seqno=" + getSeqNo() + ", h=" + history + ", " +
                nondet + ", " + reqBatch + ">";
    }

    public static void main(String args[]) {
        SignedRequestCore[] entries = new SignedRequestCore[1];
        byte[] tmp = new byte[2];
        tmp[0] = 1;
        tmp[1] = 23;
        Parameters param = new Parameters();
        entries[0] = new SignedRequestCore(param, 2, 0, 3, tmp);
        HistoryDigest hist = new HistoryDigest(param, tmp);
        RequestBatch batch = new RequestBatch(param, entries);
        NonDeterminism non = new NonDeterminism(12341, 123456);

        PrePrepare vmb = new PrePrepare(param, 43, 234, hist, batch, non, hist, 3);
        //System.out.println("initial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        PrePrepare vmb2 = new PrePrepare(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        vmb = new PrePrepare(param, 134, 8, hist, batch, non, hist, 1);
        //System.out.println("\ninitial: "+vmb.toString());
        UnsignedTypes.printBytes(vmb.getBytes());
        vmb2 = new PrePrepare(vmb.getBytes(), param);
        //System.out.println("\nsecondary: "+vmb2.toString());
        UnsignedTypes.printBytes(vmb2.getBytes());

        //System.out.println("\nold = new: "+vmb.equals(vmb2));
    }

    int cleanCount;

    public synchronized void rcCleaned() {
        cleanCount++;
        //System.err.println("CV SLAVE::" + cleanCount + ":" + this.getRequestBatch().getEntries().length);
        this.notifyAll();
    }

    public synchronized void rcMasterCleaned() {
        cleanCount++;
        try {
            //System.err.println("CV MASTER::" + cleanCount + ":" + this.getRequestBatch().getEntries().length);
            while (cleanCount < this.getRequestBatch().getEntries().length) {
                //System.out.println("waiting on this message");
                System.out.println("waiting on a master cleaner");
                this.wait();
            }
        } catch (InterruptedException e) {

        }
    }
}