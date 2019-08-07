/**
 * $Id: CPLogger.java 3946 2009-07-23 21:01:15Z aclement $
 */
package BFT.serverShim;

import BFT.serverShim.statemanagement.CheckPointState;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author riche
 */
public class CPLogger implements Runnable {

    private ShimBaseNode cpq = null;
    private CheckPointState cps = null;
    private Integer id = null;
    private String filePrefix;

    public CPLogger(ShimBaseNode cpq, CheckPointState cps, String filePrefix) {
        this.cps = cps;
        this.cpq = cpq;
        this.filePrefix = filePrefix;
    }

    public CPLogger(ShimBaseNode cpq, CheckPointState cps, int id, String filePrefix) {
        this(cpq, cps, filePrefix);
        this.id = new Integer(id);
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {

        String filename = null;
        if (id == null) {
            filename = filePrefix + cps.getMaxSequenceNumber() + "_SHIM_CP.LOG";
        } else {
            filename = filePrefix + cps.getMaxSequenceNumber() + "_" + id + "_SHIM_CP.LOG";
        }
        try {
            //BFT.//Debug.println("LOGGING CP to " + filename);
            FileOutputStream out = new FileOutputStream(filename);

            out.write(cps.getBytes());
            out.close();
        } catch (FileNotFoundException e) {
            BFT.Debug.kill(e);
        } catch (IOException e) {
            BFT.Debug.kill(e);
        }
        cps.markStable();
        cpq.makeCpStable(cps);
    }

}
