package Applications.tpcw_servlet.message;

import Applications.tpcw_servlet.util.TPCWServletUtils;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Vector;

public class TPCW_TransactionMessage implements TransactionMessageInterface {

    private static final long serialVersionUID = 1051752058645731620L;

    private Vector<String> types = null;
    private Vector<Object> data = null;

    private int noOfArgs = 0;
    private int popCursor = 0;

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(serialVersionUID);

        out.writeInt(noOfArgs);

        assert (this.popCursor == 0);

        for (int i = 0; i < this.noOfArgs; i++) {
            out.writeUTF(this.types.elementAt(i));
            out.writeObject(this.data.elementAt(i));
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        long classId = in.readLong();
        assert (classId == serialVersionUID);

        this.noOfArgs = in.readInt();
        this.popCursor = 0;

        this.types = new Vector<String>();
        this.data = new Vector<Object>();

        for (int i = 0; i < this.noOfArgs; i++) {
            this.types.add(in.readUTF());
            this.data.add(in.readObject());
        }

    }

    public TPCW_TransactionMessage() {
        this.types = new Vector<String>();
        this.data = new Vector<Object>();

        this.noOfArgs = 0;
        this.popCursor = 0;
    }

    @Override
    public <OBJ_TYPE> void pushSimpleObject(String type, OBJ_TYPE value) {
        this.noOfArgs++;
        this.types.add(type);
        this.data.add(value);
    }

    @Override
    public <ELEMENT_TYPE> void pushVector(String type, Vector<ELEMENT_TYPE> vector) {
        this.noOfArgs++;
        this.types.add(type);
        this.data.add(new VectorWrapper(vector));
    }

    @Override
    public void pushInt(int value) {
        this.pushSimpleObject(TPCWServletUtils.intMarker, new Integer(value));
    }

    @Override
    public void pushInt(Integer value) {
        this.pushSimpleObject(TPCWServletUtils.intMarker, value);
    }

    @Override
    public void pushLong(long value) {
        this.pushSimpleObject(TPCWServletUtils.longMarker, new Long(value));
    }

    @Override
    public void pushDouble(double value) {
        this.pushSimpleObject(TPCWServletUtils.doubleMarker, new Double(value));
    }

    @Override
    public void pushString(String value) {
        this.pushSimpleObject(TPCWServletUtils.stringMarker, value);
    }

    @Override
    public void pushVectorInt(Vector<Integer> values) {
        this.pushVector(TPCWServletUtils.intVectorMarker, values);
    }

    @Override
    public void pushVectorString(Vector<String> values) {
        this.pushVector(TPCWServletUtils.stringVectorMarker, values);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <RET_TYPE> RET_TYPE popSimpleObject(String objectType) {
        assert (this.noOfArgs > this.popCursor);
        String type = this.types.elementAt(this.popCursor);
        assert (type.equals(objectType));
        RET_TYPE value = (RET_TYPE) this.data.elementAt(this.popCursor);
        this.popCursor++;

        //if (!type.equals(TPCWServletUtils.intMarker))
        //    assert(value != null);
        if (value == null) {
            System.err.println("[LCOSVSE] return value is null, object type is: " + type);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ELEMENT_TYPE> Vector<ELEMENT_TYPE> popVector(String elementType) {
        assert (this.noOfArgs > this.popCursor);
        String type = this.types.elementAt(this.popCursor);
        assert (type.equals(elementType));
        Object ob = this.data.elementAt(this.popCursor);
        assert (ob instanceof VectorWrapper);
        Vector<ELEMENT_TYPE> res = (Vector<ELEMENT_TYPE>) ((VectorWrapper) ob).getVector();
        this.popCursor++;

        assert (res != null);
        return res;
    }
//	
//	@Override
//	public int popInt() {
//		return this.popSimpleObject(TPCWServletUtils.intMarker);
//	}
//	
//	@Override
//	public String popString() {
//		return this.popSimpleObject(TPCWServletUtils.stringMarker);
//	}
//	
//	@Override
//	public Vector<Integer> popVectorInt() {
//		return this.popVector(TPCWServletUtils.intVectorMarker);
//	}
//	
//	@Override
//	public Vector<String> popVectorString() {
//		return this.popVector(TPCWServletUtils.stringVectorMarker);
//	}
}
