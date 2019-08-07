// $Id: VerifyMessage.java 158 2010-04-03 04:30:08Z manos $

package BFT.messages;

import BFT.Parameters;

/**
 * @author yangwang
 */
public class NoOPMessage extends MacArrayMessage {


    public NoOPMessage(Parameters param, int sendingReplica) {
        super(param, MessageTags.NoOP,
                0,
                sendingReplica,
                param.getVerifierCount());
    }


    public NoOPMessage(byte[] bits, Parameters param) {
        super(bits, param);
        if (getTag() != MessageTags.NoOP) {
            throw new RuntimeException("invalid message Tag: " + getTag());
        }
        int offset = getOffset();
        if (offset != getBytes().length - getAuthenticationSize()) {
            throw new RuntimeException("Invalid byte input");
        }
    }

    public boolean equals(NoOPMessage nb) {
        return true;
    }

    @Override
    public String toString() {
        return "<NoOP>";
    }

    @Override
    public boolean matches(VerifiedMessageBase vmb) {
        if (vmb instanceof NoOPMessage) {
            return true;
        } else {
            return false;
        }
    }
}
