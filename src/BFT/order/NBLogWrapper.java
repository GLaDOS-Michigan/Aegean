/**
 * $Id: NBLogWrapper.java 57 2010-02-27 22:19:03Z yangwang $
 */
package BFT.order;

import BFT.messages.NextBatch;

/**
 * @author riche
 */
public class NBLogWrapper {

    private LogFileOps op;
    private NextBatch nb;
    private String fileName;

    public NBLogWrapper(NextBatch nb) {
        fileName = null;
        this.nb = nb;
    }

    public NBLogWrapper(LogFileOps op, String label) {
        this.op = op;
        fileName = label + "NB.LOG";
        this.nb = null;
    }

    /**
     * @return the nb
     */
    public NextBatch getNb() {
        return nb;
    }

    /**
     * @return the fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @return the op
     */
    public LogFileOps getOp() {
        return op;
    }

}
