/**
 * $Id: CPLogger.java 76 2010-03-07 19:56:33Z aclement $
 */
package BFT.clientShim;

import BFT.clientShim.statemanagement.CheckPointState;
import BFT.order.CPQueue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author riche
 */
public class CPLogger implements Runnable {

    private CPQueue cpq = null;
    private CheckPointState cps = null;
    private Integer id = null;
    private Integer subId = null;
    private ClientShimBaseNode csbn;

    public CPLogger(CheckPointState cps, ClientShimBaseNode csbn) {
        this.cps = cps;
        this.csbn = csbn;
    }

    public CPLogger(CheckPointState cps, int id, int sid, ClientShimBaseNode csbn) {
        this(cps, csbn);
        this.id = new Integer(id);
        this.subId = new Integer(sid);
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {

        String filename = cps.getStartSeqNum() + "_" + id + "_" +
                subId + "_CLIENT_CP.LOG";
        try {
            File tmp = new File(filename);
            if (tmp.exists()) {
                System.out.println("CP already exist!");
                return;
            }
            //BFT.//Debug.println("LOGGING CP to " + filename);
            FileOutputStream out = new FileOutputStream(filename);
            out.write(cps.getBytes());
            out.close();
        } catch (FileNotFoundException e) {
            BFT.Debug.kill(e);
        } catch (IOException e) {
            BFT.Debug.kill(e);
        }
        System.out.println("LOGGING CP to " + filename
                + " content:" + cps.getBytes());
        CheckPointState tmp = new CheckPointState(null, cps.getBytes());
//		cps.markStable();
    }
}
