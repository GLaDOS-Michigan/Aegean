// $Id

package BFT.filter.statemanagement;


import BFT.Parameters;
import BFT.messages.CommandBatch;
import BFT.messages.Digest;
import BFT.messages.Entry;

import java.util.Hashtable;


/**
 * Information related to a single batch
 **/
public class Batch {
    protected Hashtable<Long, Entry> commandList;
    protected Hashtable<Long, Entry> commands;
    protected Parameters parameters;

    public Batch(Parameters param, CommandBatch cb) {
        this.parameters = param;
        commandList = new Hashtable<Long, Entry>();
        Entry[] entries = cb.getEntries();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].getDigest() != null) {
                commandList.put(entries[i].getClient(), entries[i]);
            }
        }
        commands = new Hashtable<Long, Entry>();
    }

    public boolean addCommand(long client, Entry com) {
        Entry ent = commandList.get(client);
        if (ent == null || commands.get(client) != null)
            return false;
        Entry ent2 = com;

        if (!ent.getDigest().equals(new Digest(parameters, ent2.getCommand()))) {
            return false;
        }
        commands.put(client, com);
        commandList.remove(client);
        return true;
    }

    public Entry getCommand(long client) {
        return commands.get(client);
    }

}