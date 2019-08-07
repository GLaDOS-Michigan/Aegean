package BFT.verifier;

import BFT.messages.VerifiedMessageBase;
import BFT.verifier.messages.Commit;
import BFT.verifier.messages.MessageTags;
import BFT.verifier.messages.Prepare;

import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Apr 19, 2010
 * Time: 8:55:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class VerifierLogger extends Thread {
    private VerifierBaseNode verifier;
    private LinkedBlockingQueue<VerifiedMessageBase> queue = new LinkedBlockingQueue<VerifiedMessageBase>();
    private String prefix;

    private String currentLogName;
    private int logFileInterval = 100;

    public VerifierLogger(VerifierBaseNode verifier) {
        this.verifier = verifier;
        prefix = verifier.getParameters().verifierLoggingDirectory + File.separator + "verifier_";
    }

    public void enqueue(VerifiedMessageBase msg) {
        try {
            queue.put(msg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                VerifiedMessageBase msg = queue.take();
                if (verifier.getParameters().doLogging) {
                    long seqNo = 0;
                    if (msg.getTag() == MessageTags.Prepare) {
                        seqNo = ((Prepare) msg).getSeqNo();
                    } else if (msg.getTag() == MessageTags.Commit) {
                        seqNo = ((Commit) msg).getSeqNo();
                    } else {
                        throw new RuntimeException("Invalid message type");
                    }
                    if (seqNo % logFileInterval == 0) {
                        currentLogName = prefix + verifier.getMyVerifierIndex() + "_" + seqNo + "-" + (seqNo + logFileInterval - 1) + ".log";
                    }
                    FileOutputStream fos;
                    try {
                        fos = new FileOutputStream(new File(currentLogName), true);
                        ObjectOutputStream oos = new ObjectOutputStream(fos);
                        oos.write(msg.getBytes());
                    } catch (FileNotFoundException ex) {
                        ex.printStackTrace();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    verifier.sendToOtherVerifierReplicas(msg.getBytes(), verifier.getMyVerifierIndex());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
