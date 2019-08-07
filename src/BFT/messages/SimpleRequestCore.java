// $Id: SimpleRequestCore.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;

/**
 * Request message sent from the client to the order node.
 **/
public class SimpleRequestCore implements RequestCore {

    protected Entry entry;

    public SimpleRequestCore(Parameters param, long client, long subId, long sequence, long GC,
                             byte[] com) {
        this(new Entry(param, client, subId, sequence, GC, com));
    }

    public SimpleRequestCore(Parameters param, long client, long subId, long sequence, byte[] com) {
        this(new Entry(param, client, subId, sequence, com));
    }

    public SimpleRequestCore(Entry entry) {
        this.entry = entry;
        if (entry.has_digest()) {
            BFT.Debug.kill("simpe request core requires an actual command");
        }
    }

    public SimpleRequestCore(Parameters param, byte[] tmp) {
        entry = Entry.fromBytes(param, tmp, 0);
        if (tmp.length != entry.getSize()) {
            BFT.Debug.kill("something is borked!");
        }
    }

    public static SimpleRequestCore fromBytes(Parameters param, byte[] tmp, int off) {
        SimpleRequestCore src = new SimpleRequestCore(Entry.fromBytes(param, tmp, off));
        if (src.getEntry().has_digest()) {
            BFT.Debug.kill("simple request core should not have a digest");
        }
        return src;
    }

    public byte[] getBytes() {
        return entry.getBytes();
    }

    /* (non-Javadoc)
     * @see BFT.messages.RequestCore#getSendingClient()
	 */
    public int getSendingClient() {
        return (int) entry.getClient();
    }

    /* (non-Javadoc)
	 * @see BFT.messages.RequestCore#getSendingClient()
	 */
    public int getSubId() {
        return (int) entry.getSubId();
    }

    /*    (non-Javadoc)
	 * @see BFT.messages.RequestCore#getRequestId()
	 */
    public long getRequestId() {
        return entry.getRequestId();
    }

    public long getGCId() {
        return entry.getGCId();
    }

    /*(non-Javadoc)
	 * @see BFT.messages.RequestCore#getCommand()
	 */
    public byte[] getCommand() {
        return entry.getCommand();
    }

    public int getTotalSize() {
        return entry.getSize();
    }

    public String toString() {
        return "<" + getSendingClient() + "." + getRequestId() + ">";
    }


    public int getTag() {
        BFT.Debug.kill("HELP!");
        return -1;
    }

    public Entry getEntry() {
        return entry;
    }
}
