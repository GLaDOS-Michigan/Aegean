// $Id: MacSignatureMessage.java 292 2010-04-25 22:17:40Z glc $

package BFT.messages;

import BFT.Debug;
import BFT.Parameters;

import java.util.Arrays;

abstract public class MacSignatureMessage extends ParameterizedVerifiedMessageBase {

    public MacSignatureMessage(Parameters param, int _tag, int _size,
                               int rowcount, int rowsize) {
        super(param, _tag, _size, computeAuthenticationSize(rowcount, rowsize, param));
    }

    public MacSignatureMessage(byte[] bytes, Parameters param) {
        super(bytes, param);
        int offset = getAuthenticationStartIndex();
        byte tmp[] = new byte[MessageTags.uint32Size];
        tmp = new byte[param.digestLength];
        for (int i = 0; i < param.digestLength; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        authenticationDigest = Digest.fromBytes(param, tmp);
    }


    static public int computeAuthenticationSize(int rowcount, int rowsize, Parameters param) {
        // should be something else
        return param.digestLength + rowcount * rowsize * MacBytes.size();
    }

    public int authenticationStartIndex() {
        return digestStart();
    }

    public int getAuthenticationStartIndex() {
        return digestStart();
    }

    public int getAuthenticationEndIndex() {
        return macStartIndex();
    }

    protected int macStartIndex() {
        return authenticationStartIndex() + parameters.digestLength;
    }

    public int digestStart() {
        return getOffset() + getPayloadSize();
    }

    protected Digest authenticationDigest;

    public Digest getAuthenticationDigest() {
        if (authenticationDigest == null) {
            if (parameters.insecure) {
                authenticationDigest = new Digest(parameters);
            } else {
//                System.err.println("bytes: " + Arrays.toString(getBytes()) + ",length: " + (getTotalSize() - getAuthenticationSize()));
                authenticationDigest = new Digest(parameters, getBytes(), 0, getTotalSize() - getAuthenticationSize());
//                System.err.println("getAuthenticationDigest: " + Arrays.toString(authenticationDigest.getBytes()));
            }
            // add to byte array
            byte tmp[] = getBytes();
            int offset = digestStart();
            for (int i = 0; i < authenticationDigest.getBytes().length; i++, offset++) {
                tmp[offset] = authenticationDigest.getBytes()[i];
            }
        }
        return authenticationDigest;
    }

    public boolean checkAuthenticationDigest() {
        if (authenticationDigest == null) {
            Debug.kill("authentication must be non-null to be checked");
        }
        Digest tmp = authenticationDigest;
        authenticationDigest = null;
        return tmp.equals(getAuthenticationDigest());
    }


    public void setMacArray(int index, MacBytes[] row) {
//        System.err.println("mac array is being set");
        int offset = macStartIndex() + index * row.length * MacBytes.size();
        for (int i = 0; i < row.length; i++) {
            for (int j = 0; j < row[i].getBytes().length; j++, offset++) {
                getBytes()[offset] = row[i].getBytes()[j];
            }
        }
        if (internalBytes != null) {
            internalBytes[index] = row;
        }
    }

    protected MacBytes[][] internalBytes;

    public MacBytes[] getMacArray(int index, int rowlength) {
        if (internalBytes != null) {
//            System.err.println("mac array is not null: " + Arrays.toString(internalBytes));
            return internalBytes[index];
        }
//        System.err.println("\tgetMacArray 1");
        int authSize = getAuthenticationSize();
//        System.err.println("\tgetMacArray 1.5");
        int rowcount = 0;
        try {
            rowcount = (authSize - parameters.digestLength) / (rowlength * MacBytes.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
//        System.err.println("\tgetMacArray 2");
        MacBytes[][] internalBytes = new MacBytes[rowcount][rowlength];
//        System.err.println("\tgetMacArray 3");
        for (int i = 0; i < rowcount * rowlength; i++) {
//            System.err.println("\tgetMacArray 3."+i+".1");
            byte[] mb = new byte[MacBytes.size()];
//            System.err.println("\tgetMacArray 3."+i+".2");
            System.arraycopy(getBytes(),
                    macStartIndex() + i * mb.length,
                    mb,
                    0, MacBytes.size());
//            System.err.println("\tgetMacArray 3."+i+".3: " + Arrays.toString(mb));
            internalBytes[i / rowlength][i % rowlength] = new MacBytes(mb);
//            System.err.println("\tgetMacArray 3."+i+".4");
        }
//        System.err.println("\tgetMacArray 4");
        return internalBytes[index];
    }
}
