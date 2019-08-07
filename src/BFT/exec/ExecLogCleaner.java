package BFT.exec;

import BFT.Debug;
import BFT.Parameters;
import merkle.MerkleTreeInstance;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 20, 2010
 * Time: 9:32:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExecLogCleaner extends Thread {
    private LinkedBlockingQueue<Long> snapshotSeqNo = new LinkedBlockingQueue<Long>();
    private Parameters parameters;

    public ExecLogCleaner(Parameters param) {
        parameters = param;
    }

    public void takeSnapshotAndclean(long seqNo) {
        try {
            snapshotSeqNo.put(seqNo);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            try {
                long seqNo = snapshotSeqNo.take();
                String fileName = parameters.execLoggingDirectory + File.separator + "snapshot_" + seqNo;
                MerkleTreeInstance.getShimInstance().takeSnapshot(fileName, seqNo);
                if (seqNo == 0)
                    continue;
                String oldFileName = parameters.execLoggingDirectory + File.separator + "snapshot_" + (seqNo - parameters.execSnapshotInterval);
                File file = new File(oldFileName);
                if (file.exists()) {
                    Debug.info(Debug.MODULE_EXEC, "File gc %s\n", oldFileName);
                    file.delete();
                }
                for (long i = seqNo - parameters.execSnapshotInterval; i < seqNo; i += parameters.batchesPerExecLog) {
                    file = new File(parameters.execLoggingDirectory + File.separator + "log_" + (i + parameters.batchesPerExecLog));
                    if (file.exists()) {
                        Debug.info(Debug.MODULE_EXEC, "File gc %s\n", file.getAbsolutePath());
                        file.delete();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
