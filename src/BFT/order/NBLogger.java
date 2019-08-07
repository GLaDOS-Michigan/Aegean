/**
 * $Id: NBLogger.java 728 2011-09-11 23:44:18Z aclement $
 */
package BFT.order;

import BFT.messages.NextBatch;

import java.io.*;
import java.util.Vector;

/**
 * @author riche
 *
 */
public class NBLogger implements Runnable {

    // TODO: Implement boolean switch inside of NB to prevent double writing

    private NBLogQueue nlq;
    private int id;
    private BufferedOutputStream out;
    private OrderBaseNode obn;
    private final int THRESH = 20 * 1024;

    public NBLogger(int id, NBLogQueue nlq, OrderBaseNode ob) {
        this.nlq = nlq;
        this.id = id;
        out = null;
        this.obn = ob;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        long start;
        long finish;
        while (true) {
            int totalBytesWritten = 0;
            NBLogWrapper nbw = null;
            Vector<NBLogWrapper> writtenNBWs = new Vector<NBLogWrapper>();
            int count = 0;
            //			start = System.currentTimeMillis();
            while (totalBytesWritten < THRESH) {
                nbw = nlq.getNBWork(id, writtenNBWs.isEmpty());
                if (nbw != null) {
                    int read = handleLog(nbw);
                    if (read == -1) {
                        break;
                    } else {
                        totalBytesWritten += read;
                        if (read > 0) {
                            writtenNBWs.add(nbw);
                        }
                    }
                } else {
                    break;
                }
                count++;
            }
            try {
                if (out != null) {
                    BFT.Debug.profileStart("NBLOG");
                    out.flush();
                    BFT.Debug.profileFinis("NBLOG");
                }
            } catch (IOException e) {
                BFT.Debug.kill(e);
            }
            if (!writtenNBWs.isEmpty()) {
                this.handleNetwork(writtenNBWs);
            }
            //			finish = System.currentTimeMillis();
            //			System.out.println(id +"pulled "+count+" things" +
            //		   " in "+(finish - start));
            //			start = finish;
        }
    }

    public int handleLog(NBLogWrapper nbw) {
        int totalBytesWritten = 0;
        // check to see if we have a file operation
        if (nbw.getNb() == null) {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e1) {
                BFT.Debug.kill(e1);
            }
            if (nbw.getOp() == LogFileOps.RESUME) {
                if (nbw.getFileName() != null) {
                    try {
                        //BFT.//Debug.println("CREATE file here: " + nbw.getFileName());
                        out = new BufferedOutputStream(new FileOutputStream(nbw.getFileName(), true), 2 * THRESH);
                    } catch (FileNotFoundException e) {
                        BFT.Debug.kill(e);
                    }
                } else {
                    BFT.Debug.kill(new RuntimeException("null filename, bad idea"));
                }
            }
            if (nbw.getOp() == LogFileOps.CREATE) {
                if (nbw.getFileName() != null) {
                    try {
                        //BFT.//Debug.println("CREATE file here: " + nbw.getFileName());
                        out = new BufferedOutputStream(new FileOutputStream(nbw.getFileName()), 2 * THRESH);
                    } catch (FileNotFoundException e) {
                        BFT.Debug.kill(e);
                    }
                } else {
                    BFT.Debug.kill(new RuntimeException("null filename, bad idea"));
                }
            }
            if (nbw.getOp() == LogFileOps.DELETE) {
                if (nbw.getFileName() != null) {
                    //BFT.//Debug.println("DELETE file here: " + nbw.getFileName());
                    File f = new File(nbw.getFileName());
                    f.delete();
                } else {
                    BFT.Debug.kill(new RuntimeException("null filename, bad idea"));
                }
            }
            totalBytesWritten = -1;
        }
        // if there is a nb, write
        else {
            NextBatch nb = nbw.getNb();
            try {
                if (out != null) {
                    out.write(util.UnsignedTypes.longToBytes(nb.getTotalSize()));
                    out.write(nb.getBytes());
                } else {
                    BFT.Debug.kill(new RuntimeException("Tried to write to a null file object"));
                }
            } catch (IOException e) {
                BFT.Debug.kill(e);
            }
            totalBytesWritten = nb.getBytes().length;
        }
        return totalBytesWritten;
    }

    public void handleNetwork(Vector<NBLogWrapper> nbws) {
        //		BFT.//Debug.println("SENDING ON W&S");
        //Debug.profileStart("NB_SND");
        for (NBLogWrapper nbw : nbws) {
            obn.andSend(nbw.getNb());
        }
        //		Debug.profileFinis("NB_SND");
    }
}
