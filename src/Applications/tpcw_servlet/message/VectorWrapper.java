package Applications.tpcw_servlet.message;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Vector;

public class VectorWrapper implements Externalizable {

    private Vector<Object> vec = null;

    public VectorWrapper() {
        this.vec = null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public VectorWrapper(Vector vec) {
        this.vec = vec;
    }

    public Vector<Object> getVector() {
        return this.vec;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        int len = this.vec.size();

        out.writeInt(len);

        for (int i = 0; i < len; i++) {
            out.writeObject(this.vec.elementAt(i));
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.vec = new Vector<Object>();

        int len = in.readInt();
        for (int i = 0; i < len; i++) {
            this.vec.add(in.readObject());
        }
    }

}
