// $Id: Quorum.java 105 2010-03-21 04:46:08Z yangwang $

package BFT.messages;

import BFT.Debug;

/**
 * certificate.
 * <p/>
 * Unfortunately we cannot use Generics to implement a handful of
 * generic certificates -- Generic types must be of the form <T
 * extends Parent> and not <T implements Interface>.
 */

public class Quorum<T extends VerifiedMessageBase> {

    T val;
    VerifiedMessageBase[] entries;

    int added;
    int quorumSize;
    int indexBase;

    public Quorum(int maxSize, int targetSize, int offsetBase) {
        entries = new VerifiedMessageBase[maxSize];
        val = null;
        quorumSize = targetSize;
        indexBase = offsetBase;
        added = 0;
    }

    public boolean isComplete() {
        Debug.debug(Debug.MODULE_EXEC, "Thread %d: added=%d quorumSize=%d\n",
                Thread.currentThread().getId(), added, quorumSize);
        return added >= quorumSize;
// 	int count = 0;
// 	for (int i = 0; i < entries.length; i++)
// 	    if (entries[i]!=null){
// 		count++;
// 	    }
// 	return count >= quorumSize;
    }

    /**
     * returns true if the entry is successfully added to the current
     * set.  returns false if the quorum must be reset to accomodate
     * the entry
     */
    public boolean addEntry(T rep) {
        int sender = rep instanceof ClientRequest ? ((ClientRequest) rep).getSubId() : rep.getSender();
        /*if(rep instanceof ClientRequest) {
    		System.out.println("subId = "+((ClientRequest)rep).getSubId());
    	} else {
    		System.out.println("not CR: sender = "+rep.getSender());
    	}*/

        if (val == null || val.matches(rep)) {
            //System.out.println("----matches val (or val is null). sender = "+sender);
            if (val == null && rep.isValid()) {
                //System.out.println("\t valid value");
                val = rep;
            } else if (val == null) {
                //System.out.println("\tInvalid value, returning false");
                return false;
            }
            //System.out.println("----MUST REACH THIS! sender = "+sender);
            if (entries[(int) (sender - indexBase)] == null) {
                added++;
                //System.out.println("\tadded increased to "+added);
            } else {
                //System.out.println("\tnon-null entry, not adding");
            }
            //System.out.println("----SECOND CHECKPOINT! sender = "+sender);
            entries[(int) (sender - indexBase)] = rep;
            //if(isComplete()) {System.out.println("Quorum complete");}
            //else {System.out.println("Quorum not yet complete, added = "+added+", quorumSize = "+quorumSize);}
            return true;
        } else if (rep.isValid()) { // gonna throw out all the old ones now!
            //System.out.println("Doesn't match val, but it's valid");
            clear();
            val = rep;
            entries[(int) (sender - indexBase)] = rep;
            added++;
            return false;
        } else {
            //System.out.println("not valid");
            return false;
        }
    }

    public T[] getEntries() {
        return (T[]) entries;
    }

    public boolean containsEntry(int i) {
        return entries[i] != null;
    }

    public T getEntry() {
        return val;
    }

    public void clear() {
        System.out.println("\tclearing quorum cert");
        for (int i = 0; i < entries.length; i++)
            entries[i] = null;
        val = null;
        added = 0;
    }

}