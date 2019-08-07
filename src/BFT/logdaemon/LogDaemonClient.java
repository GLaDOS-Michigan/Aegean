package BFT.logdaemon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class LogDaemonClient {
    Socket sock;
    DataOutputStream out;
    DataInputStream in;

    public LogDaemonClient(int port) throws IOException {
        sock = new Socket("localhost", port);
        out = new DataOutputStream(sock.getOutputStream());
        in = new DataInputStream(sock.getInputStream());
    }

    public synchronized void write(int tag, byte[] data) throws IOException {
        out.writeInt(LogRequest.WRITE);
        out.writeInt(tag);
        out.writeInt(data.length);
        out.write(data);
        int ret = in.readInt();
        if (ret != LogRequest.WRITE_OK)
            throw new IOException("Write Failed. Please check LogDaemon log");
    }

    public synchronized ArrayList<byte[]> read(int tag) throws IOException {
        out.writeInt(LogRequest.READ);
        out.writeInt(tag);
        int ret = in.readInt();
        if (ret != LogRequest.READ_OK)
            throw new IOException("Read Failed. Please check LogDaemon log");
        int size = in.readInt();
        ArrayList<byte[]> list = new ArrayList<byte[]>(size);
        for (int i = 0; i < size; i++) {
            int length = in.readInt();
            byte[] tmp = new byte[length];
            in.readFully(tmp);
            list.add(tmp);
        }
        return list;
    }

    public synchronized void barrier(int tag, long seqNo) throws IOException {
        out.writeInt(LogRequest.BARRIER);
        out.writeInt(tag);
        out.writeLong(seqNo);
        int ret = in.readInt();
        if (ret != LogRequest.BARRIER_OK)
            throw new IOException("Write Failed. Please check LogDaemon log");
    }

    public synchronized void gc(int tag, long seqNo) throws IOException {
        out.writeInt(LogRequest.GC);
        out.writeInt(tag);
        out.writeLong(seqNo);
        int ret = in.readInt();
        if (ret != LogRequest.GC_OK)
            throw new IOException("Write Failed. Please check LogDaemon log");
    }
}
