package Applications.benchmark;

import BFT.messages.MessageTags;
import BFT.util.UnsignedTypes;

import java.io.*;
import java.util.Arrays;

public class BenchReply implements Serializable {

    private boolean error = false;
    private byte[] data;

    public BenchReply(boolean error, byte[] data) {
        this.error = error;
        this.data = data;
    }

    public boolean isError() {
        return error;
    }

    public byte[] getData() {
        return data;
    }

    public BenchReply() {
    }

    public byte[] getBytes() {
        int length = MessageTags.uint16Size + MessageTags.uint16Size + data.length;
        byte[] ret = new byte[length];

        int offset = 0;

        // place the checkpoint flag
        byte[] tmp = UnsignedTypes.intToBytes(error ? 1 : 0);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];

        tmp = BFT.util.UnsignedTypes.intToBytes(data.length);
        for (int i = 0; i < tmp.length; i++, offset++)
            ret[offset] = tmp[i];

        System.arraycopy(data, 0, ret, offset, data.length);
        offset += data.length;

        return ret;

    }

    public byte[] getJavaBytes() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeBoolean(error);
            oos.writeInt(data.length);
            oos.write(data);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public BenchReply(byte[] bytes) {
        int offset = 0;

        byte[] tmp = new byte[MessageTags.uint16Size];
        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        error = (UnsignedTypes.bytesToInt(tmp) != 0);

        for (int i = 0; i < tmp.length; i++, offset++)
            tmp[i] = bytes[offset];
        int dataLength = BFT.util.UnsignedTypes.bytesToInt(tmp);

        data = new byte[dataLength];
        System.arraycopy(bytes, offset, data, 0, dataLength);
        if (offset + dataLength != bytes.length)
            throw new RuntimeException("Unmatched bytes");
    }

    // just keeping this constructor around
    public BenchReply(byte[] bytes, boolean isJava) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            error = ois.readBoolean();
            int size = ois.readInt();
            data = new byte[size];
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        String result = "error: " + error /*+ ", dataHash: " + data */ + ", dataContent: " + Arrays.toString(data);

        return result;
    }
}
