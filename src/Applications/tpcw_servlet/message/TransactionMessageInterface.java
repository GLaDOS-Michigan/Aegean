package Applications.tpcw_servlet.message;

import java.io.Externalizable;
import java.util.Vector;

public interface TransactionMessageInterface extends Externalizable {

    void pushInt(int value);

    void pushInt(Integer value);

    void pushLong(long value);

    void pushDouble(double value);

    void pushString(String value);

    void pushVectorInt(Vector<Integer> values);

    void pushVectorString(Vector<String> values);

    <OBJ_TYPE> void pushSimpleObject(String type, OBJ_TYPE value);

    <ELEMENT_TYPE> void pushVector(String type, Vector<ELEMENT_TYPE> vector);

//	int popInt();
//	String popString();
//	Vector<Integer> popVectorInt();
//	Vector<String> popVectorString();

    <RET_TYPE> RET_TYPE popSimpleObject(String objectType);

    <ELEMENT_TYPE> Vector<ELEMENT_TYPE> popVector(String elementType);
}
