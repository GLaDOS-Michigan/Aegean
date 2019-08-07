/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.verifier.messages;

import BFT.Parameters;
import BFT.messages.Digest;
import BFT.messages.HistoryDigest;
import BFT.util.UnsignedTypes;

/**
 * @author manos
 */
public class DigestId {
    private Digest digest;
    private int id;
    private Parameters parameters;

    public DigestId(Parameters param, Digest sd, int _id) {
        parameters = param;
        digest = sd;
        id = _id;
    }

    public DigestId(byte[] bytes, Parameters param) {
        parameters = param;
        //System.out.println("bytes length = "+bytes.length);
        int offset = 0;
        byte[] tmp = new byte[parameters.digestLength];
        System.arraycopy(bytes, offset, tmp, 0, parameters.digestLength);
        offset += parameters.digestLength;
        digest = Digest.fromBytes(parameters, tmp);

        byte[] tmp2 = new byte[MessageTags.uint16Size];
        System.arraycopy(bytes, offset, tmp2, 0, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;
        id = UnsignedTypes.bytesToInt(tmp2);

    }

    public static int size(Parameters param) {
        return param.digestLength + param.digestLength + MessageTags.uint16Size;
    }

    public Digest getDigest() {
        return digest;
    }

    public int getId() {
        return id;
    }

    public byte[] getBytes() {
        byte[] ret = new byte[parameters.digestLength + MessageTags.uint16Size];

        int offset = 0;
        System.arraycopy(digest.getBytes(), 0, ret, offset, parameters.digestLength);
        offset += parameters.digestLength;
        System.arraycopy(UnsignedTypes.intToBytes(id), 0, ret, offset, MessageTags.uint16Size);
        offset += MessageTags.uint16Size;

        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        DigestId target = (DigestId) obj;
        return this.digest.equals(target.digest) && this.id == target.id;
    }

    @Override
    public String toString() {
        return "<" + UnsignedTypes.bytesToHexString(getDigest().getBytes()) + ", " + id + ">";
    }

    public static void main(String[] args) {
        String stmp = "what are we doing today";
        Parameters param = new Parameters();
        HistoryDigest d = new HistoryDigest(param, stmp.getBytes());
        int testId = 2;
        DigestId hss1 = new DigestId(param, d, testId);

        byte[] firstBytes = hss1.getBytes();
        System.out.println("initial: " + hss1.toString());
        UnsignedTypes.printBytes(hss1.getBytes());
        DigestId hss2 = new DigestId(hss1.getBytes(), param);
        byte[] secondBytes = hss2.getBytes();
        System.out.println("\nsecondary: " + hss2.toString());
        UnsignedTypes.printBytes(hss2.getBytes());

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
