package BFT.exec;

import BFT.Debug;
import BFT.messages.VerifyMessage;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 19, 2010
 * Time: 8:55:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExecLogger extends Thread {
    private ExecBaseNode exec;
    private LinkedBlockingQueue<VerifyMessage> queue = new LinkedBlockingQueue<VerifyMessage>();
    ObjectOutputStream oos = null;

    public ExecLogger(ExecBaseNode exec) {
        this.exec = exec;
    }

    public void enqueue(VerifyMessage msg) {
        try {
            queue.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            try {
                VerifyMessage msg = queue.take();
                if (exec.getParameters().doLogging) {
                    if (msg.getVersionNo() % exec.getParameters().batchesPerExecLog == 0) {
                        String fileName = exec.getParameters().execLoggingDirectory +
                                File.separator + "log_" +
                                (msg.getVersionNo() + exec.getParameters().batchesPerExecLog);
                        if (oos != null)
                            oos.close();
                        oos = new ObjectOutputStream(new FileOutputStream(fileName));
                        Debug.fine(Debug.MODULE_EXEC, "%s created\n", fileName);
                    } else if (oos == null) {
                        long startSeqNo = msg.getVersionNo() - msg.getVersionNo() % exec.getParameters().batchesPerExecLog;
                        String fileName = exec.getParameters().execLoggingDirectory +
                                File.separator + "log_" +
                                (startSeqNo + exec.getParameters().batchesPerExecLog);
                        oos = new ObjectOutputStream(new FileOutputStream(fileName, true));
                        Debug.fine(Debug.MODULE_EXEC, "%s created with append model\n", fileName);
                    }
                    MerkleTreeInstance.getShimInstance().writeLog(oos, msg.getVersionNo());
                }
                exec.sendToAllVerifierReplicas(msg.getBytes());
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }
        }
    }
}
