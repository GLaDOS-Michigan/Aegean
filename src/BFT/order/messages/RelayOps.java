// $Id: RelayOps.java 722 2011-07-04 09:24:35Z aclement $

package BFT.order.messages;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.CertificateEntry;
import BFT.messages.MacMessage;
import util.UnsignedTypes;

/**

 **/
public class RelayOps extends MacMessage {


    public RelayOps(CertificateEntry cert, long seqno, int sendingReplica) {
        super(MessageTags.RelayOps, computeSize(cert), sendingReplica);

        certEntry = cert;
        seqNo = seqno;

        int offset = getOffset();
        byte[] bytes = getBytes();

        // record the sequence number
        byte[] tmp;
        UnsignedTypes.longToBytes(seqno, bytes, offset);
        offset += UnsignedTypes.uint32Size;
        tmp = cert.getBytes();
        // put down the certificateentry
        for (int i = 0; i < tmp.length; i++, offset++) {
            bytes[offset] = tmp[i];
        }

        if (offset != getBytes().length - getAuthenticationSize()) {
            Debug.kill("error in writing bytes down");
        }
    }

    public RelayOps(byte[] bits, Parameters param) {
        super(bits);
        int offset = getOffset();
        byte[] tmp;

        seqNo = UnsignedTypes.bytesToLong(bits, offset);
        offset += UnsignedTypes.uint32Size;

        tmp = new byte[getBytes().length - getAuthenticationSize() - offset];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bits[offset];
        }
        certEntry = new CertificateEntry(tmp, 0, param);

        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("invalid byte array");
        }
    }

    protected long seqNo;

    public long getSequenceNumber() {
        return seqNo;
    }

    protected CertificateEntry certEntry;

    public CertificateEntry getEntry() {
        return certEntry;
    }

    public int getSendingReplica() {
        return (int) getSender();
    }

    /**
     * computes the size of the bits specific to RelayOps
     **/
    private static int computeSize(CertificateEntry cert) {
        return cert.getSize() + MessageTags.uint32Size;
    }
}