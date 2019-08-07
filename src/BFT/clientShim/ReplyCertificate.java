// $Id: ReplyCertificate.java 49 2010-02-26 19:33:49Z yangwang $

package BFT.clientShim;

import BFT.messages.Reply;
import BFT.order.Parameters;

/**
 * Reply certificate.
 * <p>
 * Unfortunately we cannot use Generics to implement a handful of
 * generic certificates -- Generic types must be of the form <T
 * extends Parent> and not <T implements Interface>.
 **/

public class ReplyCertificate {

    Reply[] replies;
    Reply val;
    Parameters parameters;

    public ReplyCertificate(Parameters param) {
        parameters = param;
        replies = new Reply[parameters.getExecutionCount()];
        val = null;
    }

    public boolean isComplete() {
        int count = 0;
        for (int i = 0; i < replies.length; i++) {
            if (replies[i] != null) {
                count++;
            }
        }
        return count >= parameters.rightExecutionQuorumSize();
    }

    public void addReply(Reply rep) {
        if (val == null || val.matches(rep)) {
            if (val == null) {
                val = rep;
            }
            replies[(int) (rep.getSendingReplica())] = rep;
        } else { // gonna throw out all the old ones now!
            clear();
            val = rep;
            replies[(int) (rep.getSendingReplica())] = rep;
        }
    }

    public Reply getReply() {
        if (!isComplete()) {
            throw new RuntimeException("getting an incomplete reply");
        }
        int i = 0;
        for (i = 0; i < replies.length && replies[i] == null; i++) ;
        return replies[i];
    }

    public void clear() {
        for (int i = 0; i < replies.length; i++) {
            replies[i] = null;
        }
        val = null;
    }

}