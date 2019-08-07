package merkle;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: iodine
 * Date: Jan 31, 2010
 * Time: 1:04:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestTools extends TestCase {

    public void testCopyInt() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.intValue = 1;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1, item2.intValue);
    }

    public void testCopyLong() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.longValue = 1L;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1L, item2.longValue);
    }

    public void testCopyByte() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.byteValue = 1;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1, item2.byteValue);
    }

    public void testCopyShort() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.shortValue = 1;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1, item2.shortValue);
    }

    public void testCopyChar() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.charValue = 'a';
        Tools.copyObject(item1, item2);
        Assert.assertEquals('a', item2.charValue);
    }

    public void testCopyDouble() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.doubleValue = 1.0;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1.0, item2.doubleValue);
    }

    public void testCopyFloat() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.floatValue = 1.0f;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(1.0f, item2.floatValue);
    }

    public void testCopyBoolean() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.booleanValue = false;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(false, item2.booleanValue);
    }

    public void testCopyArray() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.byteArray = new byte[4];
        Tools.copyObject(item1, item2);
        Assert.assertTrue(item1.byteArray == item2.byteArray);
    }

    public void testCopyRef() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.reference = new String("haha");
        Tools.copyObject(item1, item2);
        Assert.assertTrue(item1.reference == item2.reference);
    }

    public void testCopyEnum() throws Exception {
        DataItem item1 = new DataItem();
        DataItem item2 = new DataItem();
        item1.type = DataItem.Type.TYPE2;
        Tools.copyObject(item1, item2);
        Assert.assertEquals(DataItem.Type.TYPE2, item2.type);
    }

    public void testClone() throws Exception {
        DataItem item1 = new DataItem();
        item1.intValue = 1;
        item1.longValue = 2L;
        item1.shortValue = 3;
        item1.byteValue = 4;
        item1.charValue = 'a';
        item1.doubleValue = 1.0;
        item1.floatValue = 1.0f;
        item1.booleanValue = false;
        item1.byteArray = new byte[5];
        item1.reference = new String("Wooo");
        item1.type = DataItem.Type.TYPE1;
        DataItem item2 = (DataItem) Tools.cloneObject(item1);
        Assert.assertEquals(item1.intValue, item2.intValue);
        Assert.assertEquals(item1.longValue, item2.longValue);
        Assert.assertEquals(item1.shortValue, item2.shortValue);
        Assert.assertEquals(item1.byteValue, item2.byteValue);
        Assert.assertEquals(item1.charValue, item2.charValue);
        Assert.assertEquals(item1.doubleValue, item2.doubleValue);
        Assert.assertEquals(item1.floatValue, item2.floatValue);
        Assert.assertEquals(item1.booleanValue, item2.booleanValue);
        Assert.assertEquals(item1.byteArray, item2.byteArray);
        Assert.assertEquals(item1.reference, item2.reference);
        Assert.assertEquals(item1.type, item2.type);
    }

    public void testSerialize() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1 = new DataItem();
        item1.intValue = 1;
        item1.longValue = 2L;
        item1.shortValue = 3;
        item1.byteValue = 4;
        item1.charValue = 'a';
        item1.doubleValue = 1.0;
        item1.floatValue = 1.0f;
        item1.booleanValue = false;
        item1.type = DataItem.Type.TYPE1;
        tree.add(item1);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, item1, tree);
        oos.flush();
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref = new ArrayList<Object>();
        DataItem item2 = (DataItem) Tools.readObject(ois, ref);
        Assert.assertEquals(item1.intValue, item2.intValue);
        Assert.assertEquals(item1.longValue, item2.longValue);
        Assert.assertEquals(item1.shortValue, item2.shortValue);
        Assert.assertEquals(item1.byteValue, item2.byteValue);
        Assert.assertEquals(item1.charValue, item2.charValue);
        Assert.assertEquals(item1.doubleValue, item2.doubleValue);
        Assert.assertEquals(item1.floatValue, item2.floatValue);
        Assert.assertEquals(item1.booleanValue, item2.booleanValue);
        Assert.assertEquals(item1.byteArray, item2.byteArray);
        Assert.assertEquals(item1.reference, item2.reference);
        Assert.assertEquals(item1.type, item2.type);
        Assert.assertEquals(3, ref.size());
        Assert.assertEquals(-1, ((Integer)ref.get(0)).intValue());
        Assert.assertEquals(-1, ((Integer)ref.get(1)).intValue());
    }

    public void testSerializeArray() throws Exception {
        MerkleTree tree = new MerkleTree();
        int[] values = new int[2];
        values[0] = 123;
        values[1] = 456;
        tree.add(values);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, values, tree);
        oos.flush();
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref = new ArrayList<Object>();
        int[] values2 = (int[]) Tools.readObject(ois, ref);
        Assert.assertEquals(values[0], values2[0]);
        Assert.assertEquals(values[1], values2[1]);
    }

    public void testSerializeEnumArray() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem.Type[] types = new DataItem.Type[2];
        types[0] = DataItem.Type.TYPE1;
        types[1] = DataItem.Type.TYPE2;
        tree.add(types);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, types, tree);
        oos.flush();
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref = new ArrayList<Object>();
        DataItem.Type[] types2 = (DataItem.Type[]) Tools.readObject(ois, ref);
        Assert.assertEquals(types[0], types2[0]);
        Assert.assertEquals(types[1], types2[1]);
    }

    public void testSerializeReference() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1=new DataItem();
        DataItem item2=new DataItem();
        item1.reference = item2;
        item2.reference = item1;
        tree.add(item1);
        tree.add(item2);
        tree.finishThisVersion();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, item1, tree);
        Tools.writeObject(oos, item2, tree);
        oos.flush();
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref1 = new ArrayList<Object>();
        DataItem tmp1=(DataItem)Tools.readObject(ois,ref1);
        Assert.assertEquals(3,ref1.size());
        Assert.assertEquals(-1, ((Integer)ref1.get(0)).intValue());
        Assert.assertEquals(1, ((Integer)ref1.get(1)).intValue());
        ArrayList<Object> ref2 = new ArrayList<Object>();
        DataItem tmp2=(DataItem)Tools.readObject(ois,ref2);
        Assert.assertEquals(3,ref2.size());
        Assert.assertEquals(-1, ((Integer)ref2.get(0)).intValue());
        Assert.assertEquals(0, ((Integer)ref2.get(1)).intValue());
        tree.add(tmp1);
        tree.add(tmp2);
        tree.finishThisVersion();
        Tools.connectObject(tmp1,tree,ref1);
        Tools.connectObject(tmp2,tree,ref2);
        Assert.assertEquals(tmp1.reference,tmp2);
        Assert.assertEquals(tmp2.reference,tmp1);
    }

    public void testMerkleTreeNotSerializable() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1 = new DataItem();
        item1.nonSerializable = 'a';
        tree.add(item1);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, item1, tree);
        oos.flush();
        Assert.assertEquals('a', item1.nonSerializable);
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref = new ArrayList<Object>();
        DataItem item2 = (DataItem) Tools.readObject(ois, ref);
        Assert.assertEquals(0, item2.nonSerializable);
        
    }

    public void testMerkleTreeDirectSerializable() throws Exception {
        MerkleTree tree = new MerkleTree();
        DataItem item1 = new DataItem();
        String str=new String("123");
        item1.directSerializable = str;
        DataItem test=(DataItem)Tools.cloneObject(item1);
        Assert.assertTrue(str.equals(test.directSerializable));
        Assert.assertFalse(str==test.directSerializable);
        tree.add(item1);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        Tools.writeObject(oos, item1, tree);
        oos.flush();
        Assert.assertEquals("123", item1.directSerializable);
        tree = new MerkleTree();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<Object> ref = new ArrayList<Object>();
        DataItem item2 = (DataItem) Tools.readObject(ois, ref);
        Assert.assertEquals("123", item2.directSerializable);

    }

    public void testMerkleTreeStaticDirectSerializable() throws Exception {
        MerkleTree tree = new MerkleTree();

        DataItem.staticDirectSerializable = new String("123");
        tree.addStatic(DataItem.class);

        byte []tmp=Tools.serializeStatic(DataItem.class, tree);
        Assert.assertEquals("123", DataItem.staticDirectSerializable);
        tree = new MerkleTree();

        Tools.deserializeStatic(tmp,tree);
        Assert.assertEquals("123", DataItem.staticDirectSerializable);

    }

    public static class InternalClass{
        public int value1;
    }

    public void testInternalClass() throws Exception{
        InternalClass item=new InternalClass();
        item.value1=123;
        InternalClass copy=(InternalClass)Tools.cloneObject(item);
        Assert.assertEquals(123, copy.value1);
    }

    public void testGetReference() throws Exception{
        DataItem item=new DataItem();
        Integer tmp = new Integer(2);
        byte []tmp2 = new byte[4];
        item.reference = tmp;
        item.byteArray = tmp2;
        List<Object> ret = Tools.getAllReferences(item);
        Assert.assertEquals(2, ret.size());
        Assert.assertTrue(ret.contains(tmp));
        Assert.assertTrue(ret.contains(tmp2));
    }
}
