// $Id: NextBatchCertificate.java 3935 2009-07-23 07:23:55Z aclement $

package BFT.serverShim.statemanagement;

import BFT.Parameters;
import BFT.messages.CommandBatch;
import BFT.messages.Entry;
import BFT.messages.MessageTags;
import BFT.messages.NextBatch;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * NextBatch certificate.
 * <p>
 * Unfortunately we cannot use Generics to implement a handful of
 * generic certificates -- Generic types must be of the form <T
 * extends Parent> and not <T implements Interface>.
 **/

public class NextBatchCertificate {

    int[] batches;
    NextBatch val;
    Hashtable<Long, Entry> commandList;
    Hashtable<Long, Entry> commands;
    Parameters parameters;

    public NextBatchCertificate(Parameters param) {
        parameters = param;
        batches = new int[parameters.getOrderCount()];
        val = null;
        commandList = new Hashtable<Long, Entry>();
        commands = new Hashtable<Long, Entry>();
        result = null;
        completed = false;
    }

    protected boolean completed;

    public boolean isComplete() {
        if (completed) return completed;
        int Ccount = 0;
        int Tcount = 0;
        int Scount = 0;
        for (int i = 0; i < batches.length; i++) {
            if (batches[i] == MessageTags.SpeculativeNextBatch) {
                Scount++;
            } else if (batches[i] == MessageTags.TentativeNextBatch) {
                Tcount++;
                Scount++;
            } else if (batches[i] == MessageTags.CommittedNextBatch) {
                Ccount++;
                Tcount++;
                Scount++;
            }
        }
        // 	if (val!=null)
        // 	    BFT.Debug.println("\t\t"+val.getVersionNo()+" spec: "+
        // 			       Scount+" tent: "+Tcount+" com: "+Ccount);
        // 	else
        // 	    BFT.Debug.println("\t\tnothing in batch yet");

        completed = Ccount >= parameters.smallOrderQuorumSize() ||
                Tcount >= parameters.largeOrderQuorumSize() ||
                Scount >= parameters.fastOrderQuorumSize();
        return completed;
    }

    public void forceCompleted() {
        if (val == null)
            BFT.Debug.kill("cant complete an empty certificate");
        completed = true;
    }

    public void addNextBatch(NextBatch rep) {
// 	System.out.println("\tadding "+rep +" " +rep.getTag());
// 	System.out.println("\t    to "+val + " "+((val==null)?null:val.getTag()));
        if (val != null && val.getSeqNo() > rep.getSeqNo())
            return; // throw out 'old' nextbatches
        if (val != null && val.matches(rep)) {
            if (rep.getTag() > val.getTag())
                val = rep;
            if (rep.getTag() > batches[(int) (rep.getSendingReplica())]) {
                batches[(int) (rep.getSendingReplica())] = rep.getTag();
            }
            if (rep.getTag() == 13) {
// 	    	System.out.println("\t\tadding "+rep.getTag() +" for "+rep.getVersionNo());
// 		System.out.println(rep);
            }
        } else if (completed && !val.consistent(rep)) {
            //System.out.println("replacing "+val);
            //System.out.println("with      "+rep);
            //BFT.Debug.kill("Bad things, trying to replace something thats completed");
        } else { // gonna throw out all the old ones now!
            //BFT.//Debug.println("clearing a nextbatch!");
// 	    System.out.println("\t\tclearing for "+rep.getTag());
// 	    System.out.println("val: "+val);
// 	    System.out.println("rep: "+rep);
            clear();
            if (!rep.checkAuthenticationDigest())
                BFT.Debug.kill("BAD authentication digest");
            val = rep;
            CommandBatch cb = rep.getCommands();
            Entry[] entries = cb.getEntries();
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].getDigest() != null)
                    commandList.put(entries[i].getClient(),
                            entries[i]);
                else {
                    commands.put(entries[i].getClient(),
                            entries[i]);
                }
            }
            batches[(int) (rep.getSendingReplica())] = rep.getTag();

        }
    }

    public NextBatch getNextBatch() {
        if (!isComplete())
            throw new RuntimeException("getting an incomplete nextBatch");
        if (val == null)
            throw new RuntimeException("something is wrong");
        return val;
    }


    public NextBatch previewNextBatch() {
        return val;
    }


    public boolean isReadyForExecution() {
        if (!isComplete()) {
// 	    if (val == null)
// 		System.out.println("\t\tunknown batch not yet complete");
// 	    else
// 		System.out.println("\t\t"+val.getVersionNo()+" batch not yet complete");
            return false;
        }
        boolean res = commandList.isEmpty();
// 	 	if (!res){
// 		    Enumeration<Entry> miss = commandList.elements();
// 		    String tmp = "{";
// 		    while(miss.hasMoreElements()){
// 			Entry etmp = miss.nextElement();
// 			tmp = tmp+"<"+etmp.getClient()+","+etmp.getRequestId()+">, ";
// 		    }
// 		    tmp = tmp +"}";
// 		    System.out.println("\t\t"+val.getVersionNo()+" missing commands: "+tmp);
// 	}
        return res;
    }

    public boolean addCommand(long client, Entry com) {
        Entry ent = commandList.get(client);
        if (ent == null || commands.get(client) != null)
            return false;
        Entry ent2 = com;
        if (!ent2.matches(ent.getDigest()))
            return false;
        commands.put(client, com);
        commandList.remove(client);
        return true;
    }


    public Enumeration<Entry> getMissingEntries() {
        return commandList.elements();
    }


    CommandBatch result;

    public CommandBatch getCommands() {
        if (result != null)
            return result;
        if (!isComplete())
            return null;

        CommandBatch cb = val.getCommands();
        Entry[] tmp = cb.getEntries();
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = commands.get(tmp[i].getClient());
        }
        result = new CommandBatch(tmp);
        return result;
    }


    public void clear() {
        completed = false;
        for (int i = 0; i < batches.length; i++)
            batches[i] = 0;
        val = null;
        commandList.clear();
        commands.clear();
        result = null;
    }


}