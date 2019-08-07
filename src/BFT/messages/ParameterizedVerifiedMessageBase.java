// $Id: VerifiedMessageBase.java 722 2011-07-04 09:24:35Z aclement $


package BFT.messages;

import BFT.Parameters;

public abstract class ParameterizedVerifiedMessageBase extends VerifiedMessageBase {

    public ParameterizedVerifiedMessageBase(Parameters param, int _tag, int _size,
                                            int _auth_size) {
        super(_tag, _size, _auth_size);
        parameters = param;
    }

    public ParameterizedVerifiedMessageBase(byte[] bytes) {
        super(bytes);
    }

    public ParameterizedVerifiedMessageBase(byte[] bytes, Parameters param) {
        super(bytes);
        parameters = param;
    }

    protected Parameters parameters;

    public String toString() {
        return "<PVMB, tag:" + getTag() + ", payloadSize:" + getPayloadSize() + ", sender:" + getSender() + ">";
    }

}



