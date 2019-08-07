/**
 * $Id: MsgLogWrapper.java 69 2010-03-06 03:11:46Z yangwang $
 */
package BFT.filter;

import BFT.messages.Entry;
import BFT.messages.FilteredRequestCore;

/**
 * @author riche
 */
public class MsgLogWrapper {

    private LogFileOps op;
    private FilteredRequestCore frc = null;
    private Entry entry = null;
    private long seqno = 0;


    public MsgLogWrapper(FilteredRequestCore nb, Entry entry) {
        this.frc = nb;
        this.entry = entry;
    }

    public MsgLogWrapper(Entry ent) {
        this.entry = ent;
    }

    public MsgLogWrapper(LogFileOps op) {
        if (op == LogFileOps.CREATE)
            BFT.Debug.kill("should not be creating with this constructor");
        this.op = op;
        this.frc = null;
    }

    public MsgLogWrapper(LogFileOps op, long val) {
        if (op != LogFileOps.CREATE)
            BFT.Debug.kill("should be creating");
        this.op = op;
        this.frc = null;
        this.seqno = val;
    }

    /**
     * @return the nb
     */
    public FilteredRequestCore getCore() {
        return frc;
    }

    /**
     * @return the op
     */
    public LogFileOps getOp() {
        return op;
    }

    /**
     * @return the entry
     */
    public Entry getEntry() {
        return entry;
    }


    public long getSeqNo() {
        return seqno;
    }

}
