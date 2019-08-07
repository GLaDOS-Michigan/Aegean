// $Id

package BFT.messages;

import BFT.Parameters;

public class CommandBatch {

    public CommandBatch(Entry[] _entries) {
        entries = _entries;
        bytes = null;
    }

    public CommandBatch(byte[] bytes, int count, Parameters param) {
        entries = Entry.getList(param, bytes, count);
    }

    protected Entry[] entries;
    protected byte[] bytes;

    public Entry[] getEntries() {
        return entries;
    }

    public int getEntryCount() {
        return entries.length;
    }

    public int getSize() {
        return getBytes().length;
    }

    public String toString() {
        String tmp = "{";
        for (int i = 0; i < entries.length; i++) {
            tmp += "<" + entries[i].getClient() + "," + entries[i].getRequestId() + ">";
        }
        return tmp;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            int totalSize = 0;
            for (int i = 0; i < entries.length; i++)
                totalSize += entries[i].getSize();
            bytes = new byte[totalSize];
            int offset = 0;

            for (int i = 0; i < entries.length; i++) {
                byte[] tmp = entries[i].getBytes();
                for (int j = 0; j < tmp.length; j++, offset++)
                    bytes[offset] = tmp[j];
            }
        }
        return bytes;
    }

    public boolean equals(CommandBatch b) {
        boolean res = b.getEntries().length == getEntries().length;
        for (int i = 0; res && i < entries.length; i++) {
            res = res && getEntries()[i].equals(b.getEntries()[i]);
        }
        return res;
    }
}