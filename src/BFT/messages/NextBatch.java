// $Id: NextBatch.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

/**
 * NextBatch message sent from the order node to the execution node
 * indicating the batch to be executed at sequence number n in view
 * view with history h using specified non determinism and possibly
 * taking a checkpoint after executing all requests in the batch.
 **/
abstract public class NextBatch extends MacArrayMessage {

    public NextBatch(Parameters param, int tag, long view, NextBatch nb) {
        this(param, tag, view, nb.getSeqNo(), nb.getHistory(), nb.getCommands(),
                nb.getNonDeterminism(), nb.getCPDigest(), nb.takeCP(), nb.getSendingReplica());
    }

    public NextBatch(Parameters param, int tag, long view, long seq, HistoryDigest hist,
                     CommandBatch batch, NonDeterminism non, Digest cphash,
                     boolean cp, long sendingReplica) {
        this(param, tag, view, seq, new CertificateEntry(param, hist, batch, non, cphash), cp, sendingReplica);
    }

    public NextBatch(Parameters param, int tag, long view, long seq, CertificateEntry entry,
                     boolean cp, long sendingReplica) {
        super(param, tag,
                computeSize(param, view, seq, entry, cp),
                sendingReplica,
                param.getExecutionCount());

        viewNo = view;
        seqNo = seq;
        certEntry = entry;
        takeCP = cp;

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
        tmp = getHistory().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the cphash
        tmp = getCPDigest().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the checkpoint flag
        UnsignedTypes.intToBytes(takeCP ? 1 : 0, bytes, offset);
        offset += UnsignedTypes.uint16Size;

        // place the nondeterminism
        // size first
        UnsignedTypes.intToBytes(getNonDeterminism().getSize(), bytes, offset);
        offset += UnsignedTypes.uint16Size;

        // now the nondet bytes
        tmp = getNonDeterminism().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        // place the size of the batch
        UnsignedTypes.longToBytes(getCommands().getSize(), bytes, offset);
        offset += UnsignedTypes.uint32Size;

        // place the number of entries in the batch
        UnsignedTypes.intToBytes(getCommands().getEntries().length, bytes, offset);
        offset += UnsignedTypes.uint16Size;

        // place the batch bytes
        tmp = getCommands().getBytes();
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }
    }

    public NextBatch(byte[] bits, Parameters param) {
        super(bits, param);
        int offset = getOffset();
        byte[] tmp;

        // read the view number;
        viewNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the sequence number
        seqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        // read the history
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        HistoryDigest history = HistoryDigest.fromBytes(tmp, param);

        // read the cphash
        tmp = new byte[param.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        Digest cpHash = Digest.fromBytes(param, tmp);

        // read the checkpoint flag
        takeCP = (UnsignedTypes.bytesToInt(bits, offset) != 0);
        offset += UnsignedTypes.uint16Size;

        // read the non det size
        int nondetSize = (UnsignedTypes.bytesToInt(bits, offset));
        offset += UnsignedTypes.uint16Size;
        // read the nondeterminism
        tmp = new byte[nondetSize];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        NonDeterminism nondet = new NonDeterminism(tmp);

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
        CommandBatch comBatch = new CommandBatch(tmp, count, param);

        certEntry = new CertificateEntry(param, history, comBatch, nondet, cpHash);

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

    public HistoryDigest getHistory() {
        return certEntry.getHistoryDigest();
    }

    public CommandBatch getCommands() {
        return certEntry.getCommandBatch();
    }

    public NonDeterminism getNonDeterminism() {
        return certEntry.getNonDeterminism();
    }

    public Digest getCPDigest() {
        return certEntry.getCPHash();
    }

    protected CertificateEntry certEntry;

    public CertificateEntry getCertificateEntry() {
        return certEntry;
    }

    protected boolean takeCP;

    public boolean takeCP() {
        return takeCP;
    }

    public long getSendingReplica() {
        return getSender();
    }

    public boolean equals(NextBatch nb) {
        return super.equals(nb) && matches(nb);
    }

    public boolean consistent(NextBatch nb) {
        return nb != null && seqNo == nb.seqNo &&
                getHistory().equals(nb.getHistory()) && takeCP == nb.takeCP &&
                getNonDeterminism().equals(nb.getNonDeterminism())
                && getCommands().equals(nb.getCommands());
    }

    public boolean matches(NextBatch nb) {
        return nb != null && viewNo == nb.viewNo && consistent(nb);
    }


    /**
     * computes the size of the bits specific to NextBatch
     **/
    private static int computeSize(Parameters param, long view, long seq, CertificateEntry entry,
                                   boolean cp) {
        int size = MessageTags.uint32Size + MessageTags.uint32Size +
                MessageTags.uint16Size + MessageTags.uint16Size +
                MessageTags.uint16Size +
                MessageTags.uint32Size +//+ entry.getSize();
                param.digestLength + // manos: was: entry.getCPHash().size()+
                param.digestLength + // manos: was: entry.getHistoryDigest().size() +
                entry.getNonDeterminism().size() + entry.getCommandBatch().getSize();
        return size;
    }


//  private static int computeSize(long view, long seq, Digest h, 
// 			    CommandBatch batch, NonDeterminism non, 
// 			    boolean cp){
//      int size =  MessageTags.uint32Size + MessageTags.uint32Size +
// 	 Digest.size() + MessageTags.uint16Size + MessageTags.uint16Size + 
// 	 non.getSize() + MessageTags.uint16Size +
// 	 MessageTags.uint16Size + batch.getSize();
//      return size;
//  }

    public String toString() {
        return "<NB, type: " + getTag() + " v: " + viewNo + " seq: " + seqNo + " hist: " + getHistory() + ">";
    }
}
