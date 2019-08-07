// $Id: Quorum.java 105 2010-03-21 04:46:08Z yangwang $

package BFT.messages;

import java.util.HashMap;

/**
 * certificate.
 * <p/>
 * Unfortunately we cannot use Generics to implement a handful of
 * generic certificates -- Generic types must be of the form <T
 * extends Parent> and not <T implements Interface>.
 */

public class GoodQuorum<T extends VerifiedMessageBase> {

    VerifiedMessageBase[] entries;
    HashMap<VerifiedMessageBase, Integer> counts = new HashMap<VerifiedMessageBase, Integer>();

    private final int quorumSize;
    private final int indexBase;

    public GoodQuorum(int maxSize, int targetSize, int offsetBase) {
        entries = new VerifiedMessageBase[maxSize];
        quorumSize = targetSize;
        indexBase = offsetBase;
    }

    public boolean isComplete() {
        for (Integer count : counts.values()) {
//            System.out.println("Count: " + count + ", quorumSize: " + quorumSize);
            if (count >= quorumSize)
                return true;
        }
        return false;
    }

    /**
     * returns true if the entry is successfully added to the current
     * set.  returns false if the quorum must be reset to accomodate
     * the entry
     */
    public boolean addEntry(T rep) {
        int sender;
        boolean groupMode = false;

        if(rep instanceof BatchSuggestion) {
            groupMode = true;
            sender = ((BatchSuggestion) rep).getSubId();
        }
        else {
            sender = rep instanceof ClientRequest ? ((ClientRequest) rep).getSubId() : rep.getSender();
        }

        if (!groupMode && !rep.isValid()) //if it is batch suggestion, I will check authentication before calling addEntry
            return false;

        if (entries[(int) (sender - indexBase)] == null) {
//            System.out.println("a");
            entries[(int) (sender - indexBase)] = rep;
        } else {
            if (entries[(int) (sender - indexBase)].matches(rep))
            {
                System.out.println("b");
                return true;
            }
            else
            {
                System.out.println("c");
                return false;
            }
        }

        VerifiedMessageBase tmp = null;
        for (VerifiedMessageBase v : counts.keySet()) {
            if (v.matches(rep)) {
                tmp = v;
                break;
            }
        }
        if (tmp == null) {
//            System.out.println("first request");
            counts.put(rep, 1);
        }
        else {
//            System.out.println("second, third request");
            counts.put(tmp, counts.get(tmp) + 1);
        }
        return counts.size() > 1;
    }

    public T[] getEntries() {
        return (T[]) entries;
    }

    public boolean containsEntry(int i) {
        return entries[i] != null;
    }

    public T getEntry() {
        for (VerifiedMessageBase v : counts.keySet()) {
            if (counts.get(v) >= quorumSize)
                return (T) v;
        }
        return null;
    }


    public boolean isFull() {
        for (Integer count : counts.values()) {
//            System.err.println("Count: " + count + ", maxSize: " + entries.length);
            if (count >= entries.length)
                return true;
        }
        return false;
    }
}