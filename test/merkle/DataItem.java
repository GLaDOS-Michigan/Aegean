package merkle;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Jan 31, 2010
 * Time: 1:39:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class DataItem{
    public enum Type {
        TYPE1, TYPE2
    }

    public DataItem() {
	//System.out.println("DataItem created");
    }

    public int intValue;
    public long longValue;
    public byte byteValue;
    public char charValue;
    public short shortValue;
    public double doubleValue;
    public float floatValue;
    public boolean booleanValue;
    public byte[] byteArray;
    public Object reference;
    public Type type;
    public String str;

    public final int finalInt=123;

    public static int staticInt;
    public static Object staticReference;


    public transient char nonSerializable;

    @MerkleTreeDirectSerializable
    public Object directSerializable;

    @MerkleTreeDirectSerializable
    public static Object staticDirectSerializable;
}
