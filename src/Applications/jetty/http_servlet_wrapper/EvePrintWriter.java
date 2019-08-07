package Applications.jetty.http_servlet_wrapper;

import Applications.jetty.eve_connector.JettyEveConnector;
import Applications.jetty.eve_connector.ServletReply;
import BFT.exec.Info;
import BFT.exec.RequestInfo;
import merkle.MerkleTreeInstance;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

/**
 * @author lcosvse
 */

public class EvePrintWriter extends PrintWriter {

    private byte[] buffer = new byte[1024];
    private int currLen;
    private JettyEveConnector connector;
    private Info requestInfo;
    private boolean closed;

    private boolean rescale(int more) {
        if (this.currLen + more >= this.buffer.length) {
            byte[] newBuffer = new byte[buffer.length * 2 + more];
            for (int i = 0; i < this.currLen; i++)
                newBuffer[i] = this.buffer[i];

            this.buffer = newBuffer;
            return true;
        }
        return false;
    }

    public EvePrintWriter(JettyEveConnector connector, Info requestInfo) {
        super(new ByteArrayOutputStream()); // The pro forma o-stream is just for syntax fitting, never used. I hate this.
        this.connector = connector;
        this.requestInfo = requestInfo;
        this.closed = false;
        this.currLen = 0;
    }

    private EvePrintWriter() {
        super(new ByteArrayOutputStream());
    }

    @Override
    public final void print(String data) {
        assert (!this.closed);

        MerkleTreeInstance.update(this);

        byte[] tempBytes = data.getBytes();

        this.rescale(tempBytes.length);

        for (int i = 0; i < tempBytes.length; i++)
            this.buffer[i + this.currLen] = tempBytes[i];

        this.currLen += tempBytes.length;
    }

    @Override
    public final void println(String data) {
        assert (!this.closed);
        this.print(data + "\n");
    }

    @Override
    /*
	 * 1. Mark the current Writer as #closed#.
	 * 2. Signal the reply collector with the ready output data.
	 */
    public final void close() {
        MerkleTreeInstance.update(this);
        this.closed = true;
        this.connector.replyCallback(new ServletReply(this.buffer, this.requestInfo));
    }
}
