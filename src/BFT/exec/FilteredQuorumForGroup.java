package BFT.exec;

import BFT.Parameters;
import BFT.messages.GroupClientRequests;

public class FilteredQuorumForGroup {
    protected GroupClientRequests entry;
    protected int size, small, medium, large;
    protected boolean[] hasEntry;
    protected Parameters parameters;

    public FilteredQuorumForGroup(int s, int m, int l, int maxSize, Parameters param) {
        small = s;
        medium = m;
        large = l;
        hasEntry = new boolean[maxSize];
        parameters = param;
    }

    public void add(GroupClientRequests gcr, int snd) {
        if (size == 0 || gcr.matches(entry)) {
            if (size == 0) {
                entry = gcr;
            }
            if (!hasEntry[snd]) {
                size++;
                hasEntry[snd] = true;
            }
        } else {
            clear();
            add(gcr, snd);
        }
    }

    public void clear() {
        size = 0;
        entry = null;
        for (int i = 0; i < hasEntry.length; i++) {
            hasEntry[i] = false;
        }
    }

    public GroupClientRequests getEntry() {
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

    public boolean isComplete() {
        return size >= medium;
    }
}
