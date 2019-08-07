package Applications.tpcw_servlet.message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class TransactionMessageWrapper implements
        TransactionMessageWrapperInterface {

    private static final long serialVersionUID = 3525183273591699212L;

    private String txType = null;
    private Object message = null;

    public TransactionMessageWrapper() {
        this.txType = null;
        this.message = null;
    }

    public TransactionMessageWrapper(String txType, Object message) {
        this.txType = txType;
        this.message = message;
    }

    @Override
    public String getTransactionType() {
        return this.txType;
    }

    @Override
    public Object getMessageData() {
        return this.message;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(txType);
        out.writeObject(message);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.txType = in.readUTF();
        try {
            this.message = in.readObject();
        } catch (Exception e) {
            System.err.println("BUGGY POINT 1: " + this.txType);
        }
    }

}
