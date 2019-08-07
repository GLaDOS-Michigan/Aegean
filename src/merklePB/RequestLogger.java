/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merklePB;

import java.security.MessageDigest;
import java.util.LinkedList;

/**
 * @author yangwang
 */
public class RequestLogger {

    private LinkedList<byte[]> logs = new LinkedList<byte[]>();

    public void addEntry(byte[] req) {
        logs.add(req);
    }

    public byte[] writeLog(String fileName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            digest.reset();
            for (byte[] req : logs) {
                digest.update(req);
            }
            logs.clear();
            return digest.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("RequestLogger <reqSize> <reqNum> <batchSize>");
            return;
        }
        int reqSize = Integer.parseInt(args[0]);
        int reqNum = Integer.parseInt(args[1]);
        int batchSize = Integer.parseInt(args[2]);
        RequestLogger logger = new RequestLogger();
        byte[] req = new byte[reqSize];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < reqNum / batchSize; i++) {
            for (int j = 0; j < batchSize; j++)
                logger.addEntry(req);
            logger.writeLog(null);
        }
        System.out.println("Throughput = " + (reqNum) / (System.currentTimeMillis() - startTime));
    }
}
