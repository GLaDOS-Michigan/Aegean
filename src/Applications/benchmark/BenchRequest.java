package Applications.benchmark;

import java.io.Serializable;

public class BenchRequest implements Serializable {
    public static final byte READ = 0;
    public static final byte WRITE = 1;
    private byte type;
    private int index;
    private byte[] data;

    BenchRequest(byte type, int index, byte[] data) {
        this.type = type;
        this.index = index;
        this.data = data;
    }

    public byte getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public byte[] getData() {
        return data;
    }

    public BenchRequest() {
    }

}
