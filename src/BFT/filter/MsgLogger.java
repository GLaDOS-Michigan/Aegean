/**
 * $Id: MsgLogger.java 722 2011-07-04 09:24:35Z aclement $
 */
package BFT.filter;

import BFT.Debug;
import BFT.Parameters;
import BFT.messages.Entry;
import BFT.messages.FilteredRequestCore;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author riche
 *
 */
public class MsgLogger implements Runnable {

    // TODO: Implement boolean switch inside of NB to prevent double writing

    private MsgLogQueue nlq;
    private int id;
    private BufferedOutputStream out;
    private FilterBaseNode fbn;
    private final int THRESH = 100 * 1024;
    private final String fileNameNormal;
    private final String fileNameDump;
    private Parameters parameters;

    public MsgLogger(Parameters param, int id, MsgLogQueue nlq, FilterBaseNode fbn) {
        this.parameters = param;
        this.nlq = nlq;
        this.id = id;
        out = null;
        this.fbn = fbn;
        fileNameNormal = "normallog_" + this.id + ".LOG";
        fileNameDump = "dumplog_" + this.id + ".LOG";
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        int runningcount = 0;
        int writes = 0;
        int bias = 4 + parameters.getFilterCount();
        long start = System.currentTimeMillis();
        long fetchtime = 0;
        long waittime = 0;
        while (true) {

            //System.out.println("Top of logger loop" + id);
            int totalBytesWritten = 0;
            MsgLogWrapper nbw = null;
            Set<MsgLogWrapper> writtenNBWs = new HashSet<MsgLogWrapper>();
            //System.out.println(totalBytesWritten);
            //	    int count = 0;
            //waittime = System.currentTimeMillis();
            nlq.hasWork(id);
            //start = System.currentTimeMillis();
            while (totalBytesWritten < THRESH && nlq.size(id) > 0) {
                //&& count < (BFT.Parameters.getNumberOfClients()+bias)/bias) {
                nbw = nlq.getNBWork(id);
                if (nbw != null) {
                    int read = handleLog(nbw);
                    if (read == -1) {
                        break;
                    } else {
                        //	count++;
                        totalBytesWritten += read;
                        if (read > 0) {
                            writtenNBWs.add(nbw);
                        }
                    }
                } else
                    break;
            }

            try {
                if (out != null) {
                    BFT.Debug.profileStart("ENTRYLOG");
                    out.flush();
                    BFT.Debug.profileFinis("ENTRYLOG");
                }
            } catch (IOException e) {
                BFT.Debug.kill(e);
            }
            if (!writtenNBWs.isEmpty()) {
                this.handleNetwork(writtenNBWs);
            }
            // fetchtime = System.currentTimeMillis();
            //System.out.println(id+" spent "+(start - waittime) +" waiting and "+(fetchtime -  start) +" reading and writing "+count +"things");
        }
    }

    public int handleLog(MsgLogWrapper nbw) {
        int totalBytesWritten = 0;
        // check to see if we have a file operation
        if (nbw.getCore() == null) {

            if (nbw.getOp() == LogFileOps.CREATE) {
                try {
                    try {
                        if (out != null) {
                            out.close();
                            out = null;
                        }
                    } catch (IOException e1) {
                        BFT.Debug.kill(e1);
                    } finally {
                        out = null;
                    }
                    File file = new File(fileNameNormal);
                    if (file.exists()) {
                        file.delete();
                    }
                    file = new File(fileNameDump);
                    if (file.exists()) {
                        file.delete();
                    }
                    //System.out.println("CREATE file here: " + fileNameNormal);
                    out = new BufferedOutputStream(new FileOutputStream(fileNameNormal), 2 * THRESH);
                    out.write(util.UnsignedTypes.longToBytes(nbw.getSeqNo()));
                } catch (FileNotFoundException e) {
                    BFT.Debug.kill(e);
                } catch (IOException e) {
                    BFT.Debug.kill(e);
                }
            }
            if (nbw.getOp() == LogFileOps.START_DUMP) {
                try {
                    //System.out.println("DUMP file here: " + fileNameDump);
                    out = new BufferedOutputStream(new FileOutputStream(fileNameDump), 2 * THRESH);
                } catch (FileNotFoundException e) {
                    BFT.Debug.kill(e);
                }
            }
            totalBytesWritten = -1;
        }
        // if there is a nb, write
        else {
            Entry entry = null;
            //	    if(nbw.getCore() != null) {
            //	entry = nbw.getCore().getEntry();
            //}
            //else {
            entry = nbw.getEntry();
            //}
            try {
                if (out != null) {
                    //System.out.println("Writing " + entry.getRequestId() + " " + entry.getClient() + " ");
                    out.write(util.UnsignedTypes.longToBytes(entry.getBytes().length));
                    out.write(entry.getBytes());
                } else {
                    BFT.Debug.kill(new RuntimeException("Tried to write to a null file object"));
                }
            } catch (IOException e) {
                BFT.Debug.kill(e);
            }
            totalBytesWritten = entry.getBytes().length;

        }
        return totalBytesWritten;
    }

    public void handleNetwork(Set<MsgLogWrapper> nbws) {
        //BFT.//Debug.println("SENDING ON W&S");
        Debug.profileStart("NB_SND");
        Set<FilteredRequestCore> nonNulls = new HashSet<FilteredRequestCore>();
        for (MsgLogWrapper nbw : nbws) {
            if (nbw.getCore() != null) {
                nonNulls.add(nbw.getCore());
            }
        }
        fbn.andSend(nonNulls.toArray(new FilteredRequestCore[0]));
        Debug.profileFinis("NB_SND");
    }
}
