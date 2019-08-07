// $Id

package BFT.exec;

import BFT.Parameters;
import BFT.messages.FilteredRequestCore;


public class FilteredQuorum {

    protected FilteredRequestCore entry;
    protected int size, small, medium, large;
    protected boolean[] hasEntry;
    protected Parameters parameters;

    public FilteredQuorum(int s, int m, int l, int maxSize, Parameters param) {
        small = s;
        medium = m;
        large = l;
        hasEntry = new boolean[maxSize];
        parameters = param;
    }

    public void add(FilteredRequestCore rc, int snd) {
        if (size == 0 || rc.matches(entry)) {
            if (size == 0) {
                entry = rc;
            }
            if (!hasEntry[snd]) {
                size++;
                hasEntry[snd] = true;
            }
            entry.setMacArray(snd, rc.getMacArray(snd, parameters.getExecutionCount()));
        } else {
            clear();
            add(rc, snd);
        }
    }

    public void clear() {
        size = 0;
        entry = null;
        for (int i = 0; i < hasEntry.length; i++) {
            hasEntry[i] = false;
        }
    }

    public FilteredRequestCore getEntry() {
        return entry;
    }

    public int size() {
        return size;
    }

    public boolean small() {
        return size == small;
    }

    public boolean medium() {
        return size == medium;
    }

    public boolean large() {
        return size >= large;
    }
}
