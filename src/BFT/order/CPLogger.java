/**
 * $Id: CPLogger.java 76 2010-03-07 19:56:33Z aclement $
 */
package BFT.order;

import BFT.order.statemanagement.CheckPointState;

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
    private OrderBaseNode obn;

    public CPLogger(CPQueue cpq, CheckPointState cps, OrderBaseNode obn) {
        this.cpq = cpq;
        this.cps = cps;
        this.obn = obn;
    }

    public CPLogger(CPQueue cpq, CheckPointState cps, int id, OrderBaseNode obn) {
        this(cpq, cps, obn);
        this.id = new Integer(id);
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {

        String filename = null;
        if (id == null) {
            filename = cps.getCurrentSequenceNumber() + "_CP.LOG";
        } else {
            filename = cps.getCurrentSequenceNumber() + "_" + id + "_ORDER_CP.LOG";
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
        cps.makeStable();
        obn.cpStable(cps);
    }

}
