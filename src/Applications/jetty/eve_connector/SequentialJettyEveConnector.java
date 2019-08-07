package Applications.jetty.eve_connector;

import Applications.jetty.eve_task.JettyEveTask;
import Applications.jetty.server.FakedServer;
import BFT.Parameters;
import BFT.generalcp.AppCPInterface;
import BFT.generalcp.CPAppInterface;
import BFT.generalcp.MemoryFS;
import BFT.generalcp.RequestInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

/**
 * Created by remzi on 8/29/17.
 */
public class SequentialJettyEveConnector extends JettyEveConnector implements AppCPInterface{
    private transient CPAppInterface generalCP = null;
    private long lastSeqNo = 0;
    private String syncDir;
    protected Parameters parameters = null;

    public SequentialJettyEveConnector(CPAppInterface generalCP, Parameters parameters, String syncDir) {
        super(null);
        this.parameters = parameters;
        this.syncDir = syncDir;
        this.generalCP = generalCP;
    }

    @Override
    public void execAsync(byte[] request, RequestInfo info) {
        lastSeqNo = info.getSeqNo();

        String requestURL = new String(request);

        System.err.println("Received request in sequential mode: " + requestURL + ", requestID = " + info.getRequestId());
        JettyEveTask task = new JettyEveTask(requestURL, this, info);

        task.run();
    }

    @Override
    public void execReadonly(byte[] request, int clientId, long requestId) {
        throw new RuntimeException("Readonly request execution is not implemented.");
    }

    @Override
    public void loadSnapshot(String fileName) {
        throw new RuntimeException("loadSnapshot is not implemented.");
    }

    @Override
    public void sync() {
        try {
            if (!parameters.uprightInMemory) {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(syncFile));
                oos.writeLong(lastSeqNo);
                oos.close();
            } else {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeLong(lastSeqNo);
                oos.close();
                MemoryFS.write(syncFile.getAbsolutePath(), bos.toByteArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        generalCP.syncDone(this.syncDir + "/ht_sync_" + lastSeqNo);
    }

    public synchronized void replyCallback(ServletReply servletReply) {
        generalCP.execDone(servletReply.getReplyData(), (RequestInfo) servletReply.getRequestInfo());
    }
}
