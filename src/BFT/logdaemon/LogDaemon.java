package BFT.logdaemon;

import java.io.*;
import java.util.*;

public class LogDaemon {

    public static final int TAG_ORDER_LOG = 0;
    public static final int TAG_ORDER_CP = 1;
    public static final int TAG_FILTER_LOG = 2;
    public static final int TAG_EXEC_LOG = 3;
    public static final int TAG_APP_LOG = 4;

    private String logDir = null;
    private int lastLogIndex = 0;
    private static final String LOG_PREFIX = "LOG_";
    private static int LOG_SIZE = 1048576;

    private TreeSet<Integer> existingFiles = new TreeSet<Integer>();
    // Hashtable<tag, TreeMap<barrier seqNo, logIndex>>
    private Hashtable<Integer, TreeMap<Long, Integer>> barrierIndex = new Hashtable<Integer, TreeMap<Long, Integer>>();

    // Hashtable<logIndex, HashSet<tag>>: whether file logIndex has record for
    // tag
    private Hashtable<Integer, HashSet<Integer>> logInfo = new Hashtable<Integer, HashSet<Integer>>();

    // the latest gc for a tag
    private Hashtable<Integer, Long> gcRecord = new Hashtable<Integer, Long>();

    private DataOutputStream out = null;

    private String getFileName(int logIndex) {
        return logDir + LOG_PREFIX + logIndex;

    }

    private int getIndex(String fileName) {
        if (!fileName.startsWith(logDir + LOG_PREFIX))
            return -1;
        File f = new File(fileName);
        String name = f.getName();
        return Integer.parseInt(name.substring(LOG_PREFIX.length()));
    }

    public LogDaemon(String logDir, int logSize) {
        if (!logDir.endsWith(File.separator))
            this.logDir = logDir + File.separator;
        else
            this.logDir = logDir;
        LOG_SIZE = logSize;
        try {
            this.scanFiles();
            lastLogIndex++;
            String newLogName = getFileName(lastLogIndex);
            System.out.println("Create new log file " + newLogName);
            out = new DataOutputStream(new FileOutputStream(newLogName));
            existingFiles.add(lastLogIndex);
            logInfo.put(this.lastLogIndex, new HashSet<Integer>());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void scanFiles() throws IOException {
        File dir = new File(logDir);
        File[] logs = dir.listFiles();
        for (File logFile : logs) {
            int logIndex = getIndex(logFile.getAbsolutePath());
            if (logIndex != -1) {
                System.out.println("Scan file finds " + logIndex);
                existingFiles.add(logIndex);
            } else
                continue;
            logInfo.put(logIndex, new HashSet<Integer>());
            if (logIndex > lastLogIndex)
                lastLogIndex = logIndex;
            DataInputStream dis = new DataInputStream(new FileInputStream(
                    logFile));
            while (dis.available() > 0) {
                int logTag = dis.readInt();
                int size = dis.readInt();
                if (size == -1) {
                    // This is a barrier record
                    long seqNo = dis.readLong();
                    addBarrier(logTag, seqNo);
                } else {
                    byte[] data = new byte[size];
                    dis.readFully(data);
                    logInfo.get(logIndex).add(logTag);
                }
            }
        }

    }

    public synchronized void writeData(int tag, byte[] data) throws IOException {
        out.writeInt(tag);
        out.writeInt(data.length);
        out.write(data);
        logInfo.get(this.lastLogIndex).add(tag);
    }

    public synchronized void flush() throws IOException {
        out.flush();
        if (out.size() > LOG_SIZE) {
            lastLogIndex++;
            out.close();
            String newLogName = getFileName(lastLogIndex);
            System.out.println("Switch log file to " + newLogName);
            out = new DataOutputStream(new FileOutputStream(newLogName));
            existingFiles.add(lastLogIndex);
            logInfo.put(this.lastLogIndex, new HashSet<Integer>());
        }
    }

    public synchronized ArrayList<byte[]> read(int tag) throws IOException {
        ArrayList<byte[]> result = new ArrayList<byte[]>();
        for (Integer logIndex : existingFiles) {
            DataInputStream dis = new DataInputStream(new FileInputStream(
                    getFileName(logIndex)));
            while (dis.available() > 0) {
                int logTag = dis.readInt();
                int size = dis.readInt();
                if (size == -1) {
                    // This is a barrier record
                    dis.readLong();
                } else {
                    byte[] data = new byte[size];
                    dis.readFully(data);
                    if (logTag == tag) {
                        result.add(data);
                    }
                }
            }
        }
        return result;
    }

    public synchronized void writeBarrier(int tag, long seqNo) throws IOException {
        out.writeInt(tag);
        out.writeInt(-1);
        out.writeLong(seqNo);
        addBarrier(tag, seqNo);
    }

    private void addBarrier(int tag, long seqNo) {
        if (!barrierIndex.containsKey(tag)) {
            barrierIndex.put(tag, new TreeMap<Long, Integer>());
        }
        barrierIndex.get(tag).put(seqNo, this.lastLogIndex);
    }

    private int getBarrier(int tag, long seqNo) {
        return barrierIndex.get(tag).get(seqNo);
    }

    public synchronized void gc(int tag, long seqNo) {
        boolean needCheck = false;
        int thisLog = getBarrier(tag, seqNo);
        if (!gcRecord.containsKey(tag)) {
            needCheck = true;
        } else {
            long lastSeqNo = gcRecord.get(tag);
            int previousLog = getBarrier(tag, lastSeqNo);
            if (previousLog != thisLog) {
                needCheck = true;
            }
        }
        gcRecord.put(tag, seqNo);
        if (needCheck) {
            Iterator<Integer> iter = this.existingFiles.iterator();
            while (iter.hasNext()) {
                int logIndex = iter.next();
                if (logIndex < thisLog && isGCable(logIndex)) {
                    File tmp = new File(getFileName(logIndex));
                    tmp.delete();
                    iter.remove();
                    System.out.println(tmp.getAbsolutePath() + " deleted");
                }
            }
        }
    }

    private boolean isGCable(int logIndex) {
        System.out.println("Check gc for " + logIndex);
        for (Integer tag : logInfo.get(logIndex)) {
            // Only check for tags in this log. If this log does not have a
            // specified kind of tag, we can ignore that tag.
            long lastestGCSeqNo = -1;
            if (gcRecord.containsKey(tag)) {
                lastestGCSeqNo = gcRecord.get(tag);
            }
            // If the file of the latest gc is older than this file, this file
            // is not GCable.
            System.out.println("lastestGCSeqNo=" + lastestGCSeqNo);
            if (lastestGCSeqNo != -1)
                System.out.println(getBarrier(tag, lastestGCSeqNo) + " " + logIndex);
            if (lastestGCSeqNo == -1
                    || getBarrier(tag, lastestGCSeqNo) <= logIndex) {
                return false;
            }

        }
        return true;
    }
}
