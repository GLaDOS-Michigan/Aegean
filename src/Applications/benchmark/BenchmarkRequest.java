/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Applications.benchmark;

import BFT.messages.MessageTags;

import java.io.*;
import java.util.Arrays;

/**
 * @author manos
 */
public class BenchmarkRequest implements Serializable {

    int startIndex;
    int spins;
    int reads;
    int writes;

    int prob;
    byte[] data;

    public BenchmarkRequest(int index, int sp, int re, int wr, int size, int pr) {
        startIndex = index;
        spins = sp;
        reads = re;
        writes = wr;
        prob = pr;
        data = new byte[wr * size];
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getSpins() {
        return spins;
    }

    public int getReads() {
        return reads;
    }

    public int getWrites() {
        return writes;
    }

    public int getProb() {
        return prob;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getBytes() {
        int length = 5 * MessageTags.uint16Size +    // 5 integer fields
                MessageTags.uint16Size + data.length; // data.length + data
        byte[] ret = new byte[length];

        int offset = 0;

        byte[] tmp = BFT.util.UnsignedTypes.intToBytes(startIndex);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];
        tmp = BFT.util.UnsignedTypes.intToBytes(spins);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];
        tmp = BFT.util.UnsignedTypes.intToBytes(reads);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];
        tmp = BFT.util.UnsignedTypes.intToBytes(writes);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];
        tmp = BFT.util.UnsignedTypes.intToBytes(prob);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];

        tmp = BFT.util.UnsignedTypes.intToBytes(data.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];

        System.arraycopy(data, 0, ret, offset, data.length);
        offset += data.length;

        return ret;

    }

    public void setProb(int prob) {
        this.prob = prob;
    }

    public byte[] getJavaBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeInt(startIndex);
            oos.writeInt(spins);
            oos.writeInt(reads);
            oos.writeInt(writes);
            oos.writeInt(prob);
            oos.writeInt(data.length);
            oos.write(data);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public BenchmarkRequest(byte[] bytes) {

//        System.err.println(Arrays.toString(bytes));
        int offset = 0;

        byte[] tmp = new byte[MessageTags.uint16Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        startIndex = (int) BFT.util.UnsignedTypes.bytesToInt(tmp);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        spins = (int) BFT.util.UnsignedTypes.bytesToInt(tmp);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        reads = (int) BFT.util.UnsignedTypes.bytesToInt(tmp);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        writes = (int) BFT.util.UnsignedTypes.bytesToInt(tmp);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        prob = (int) BFT.util.UnsignedTypes.bytesToInt(tmp);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        int dataLength = BFT.util.UnsignedTypes.bytesToInt(tmp);

        data = new byte[dataLength];
        System.arraycopy(bytes, offset, data, 0, dataLength);
        if (offset + dataLength != bytes.length)
            throw new RuntimeException("Unmatched bytes");
    }

    // I just keep this constructor around with a fake boolean
    public BenchmarkRequest(byte[] bytes, boolean isJava) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            startIndex = ois.readInt();
            spins = ois.readInt();
            reads = ois.readInt();
            writes = ois.readInt();
            prob = ois.readInt();
            int size = ois.readInt();
            data = new byte[size];
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        String result = "startIndex: " + startIndex + ",spins: " + spins + ", reads: " + reads + ",writes: " + writes + ", prob: " + prob /*+ ", dataHash: " + data*/ +  ",dataContent: " + Arrays.toString(data);
        return result;
    }

}
