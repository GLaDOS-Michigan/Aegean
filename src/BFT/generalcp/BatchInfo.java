package BFT.generalcp;

import BFT.Parameters;
import BFT.messages.CommandBatch;
import BFT.messages.NonDeterminism;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: Yang Wang
 * Date: 2009-2-12
 * Time: 20:21:16
 * To change this template use File | Settings | File Templates.
 */
public class BatchInfo implements Serializable {
    private CommandBatch batch;
    private long seqNo;
    private NonDeterminism time;

    //The following field do not need to serialize.
    private boolean takeCP;

    public CommandBatch getBatch() {
        return this.batch;
    }

    public long getSeqNo() {
        return this.seqNo;
    }

    public NonDeterminism getTime() {
        return this.time;
    }

    public boolean getTakeCP() {
        return this.takeCP;
    }

    public BatchInfo() {
    }

    public BatchInfo(CommandBatch batch, long seqNo, NonDeterminism time, boolean takeCP) {
        this.batch = batch;
        this.seqNo = seqNo;
        this.time = time;
        this.takeCP = takeCP;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(seqNo);
        out.writeLong(time.getTime());
        out.writeLong(time.getSeed());
        out.writeInt(batch.getEntries().length);
        byte[] tmp = batch.getBytes();
        out.writeInt(tmp.length);
        out.write(tmp);
    }

    private void readObject(Parameters param, ObjectInputStream in) throws IOException, ClassNotFoundException {
        seqNo = in.readLong();
        time = new NonDeterminism(in.readLong(), in.readLong());
        int entryNo = in.readInt();
        int len = in.readInt();
        byte[] tmp = new byte[len];
        in.readFully(tmp);
        batch = new CommandBatch(tmp, entryNo, param);
    }

}
