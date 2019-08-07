/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merkle;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import junit.framework.Assert;
import junit.framework.TestCase;
import merkle.wrapper.MTMapWrapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;


/**
 * @author yangwang
 */
public class TestMTMapWrapper extends TestCase {

    @Override
    protected void setUp() throws Exception {
        MerkleTreeInstance.clear();
    }

    public void testMapWrapper() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(i1, l1);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(i2, l2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(i3, l3);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertFalse(wrapper.containsKey(789));
        Assert.assertEquals(12345L, wrapper.get(123));
        Assert.assertEquals(45678L, wrapper.get(456));

    }

    public void testMapWrapperSerialization() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, false, false);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(i1, l1);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(i2, l2);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(i3, l3);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testMapWrapperOrder() throws Exception {
        TreeMap<Integer, Long> target = new TreeMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, true, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(i1, l1);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(i2, l2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(i3, l3);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertFalse(wrapper.containsKey(789));
        Assert.assertEquals(12345L, wrapper.get(123));
        Assert.assertEquals(45678L, wrapper.get(456));

    }

    public void testMapWrapperSerializationOrder() throws Exception {
        TreeMap<Integer, Long> target = new TreeMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, true, false, false);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(i1, l1);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(i2, l2);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(i3, l3);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testMapWrapperKeySerializable() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, true, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(123, l1);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(456, l2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(789, l3);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertFalse(wrapper.containsKey(789));
        Assert.assertEquals(12345L, wrapper.get(123));
        Assert.assertEquals(45678L, wrapper.get(456));

    }

    public void testMapWrapperSerializationKeySerializable() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, true, false);
        MerkleTreeInstance.add(wrapper);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(123, l1);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(456, l2);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(789, l3);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testMapWrapperValueSerializable() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, false, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.put(i1, 12345L);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.put(i2, 45678L);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.put(i3, 78901L);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertFalse(wrapper.containsKey(789));
        Assert.assertEquals(12345L, wrapper.get(123));
        Assert.assertEquals(45678L, wrapper.get(456));

    }

    public void testMapWrapperSerializationValueSerializable() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, false, true);
        MerkleTreeInstance.add(wrapper);
        Integer i1 = new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.put(i1, 12345L);
        Integer i2 = new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.put(i2, 45678L);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3 = new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.put(i3, 78901L);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }


    public void testMapWrapperBothSerializableOrder() throws Exception {
        TreeMap<Integer, Long> target = new TreeMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, true, true, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        wrapper.put(123, 12345L);
        wrapper.put(456, 45678L);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.put(789, 78901L);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertFalse(wrapper.containsKey(789));
        Assert.assertEquals(12345L, wrapper.get(123));
        Assert.assertEquals(45678L, wrapper.get(456));

    }

    public void testMapWrapperSerializationBothSerializableOrder() throws Exception {
        TreeMap<Integer, Long> target = new TreeMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, true, true, true);
        MerkleTreeInstance.add(wrapper);
        wrapper.put(123, 12345L);
        wrapper.put(456, 45678L);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.put(789, 78901L);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }


    public void testMapWrapperSerializationFetch() throws Exception {
        TreeMap<Integer, Long> target = new TreeMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, true, true, false);
        MerkleTreeInstance.add(wrapper);
        Long l1 = new Long(12345L);
        MerkleTreeInstance.add(l1);
        wrapper.put(123, l1);
        Long l2 = new Long(45678L);
        MerkleTreeInstance.add(l2);
        wrapper.put(456, l2);
        //System.out.println("hash1===============");
        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Long l3 = new Long(78901L);
        MerkleTreeInstance.add(l3);
        wrapper.put(789, l3);
        byte[] state = MerkleTreeInstance.getShimInstance().fetchStates(0, 1);
        Assert.assertTrue(wrapper.containsKey(123));
        Assert.assertTrue(wrapper.containsKey(456));
        Assert.assertTrue(wrapper.containsKey(789));

        //System.out.println("hash2===============");
        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().mergeStates(1, state);
        MTMapWrapper wrapper2 = (MTMapWrapper) MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.containsKey(123));
        Assert.assertTrue(wrapper2.containsKey(456));
        Assert.assertTrue(wrapper2.containsKey(789));
        //System.out.println("hash3===============");
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.containsKey(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testMapWrapperGC() throws Exception {
        HashMap<Integer, Long> target = new HashMap<Integer, Long>();
        MTMapWrapper wrapper = new MTMapWrapper(target, false, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.addRoot(wrapper);
        Integer i1 = new Integer(123);
        Long l1 = new Long(12345L);
        wrapper.put(i1, l1);
        Integer i2 = new Integer(456);
        Long l2 = new Long(45678L);
        wrapper.put(i2, l2);

        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.getShimInstance().gc();
        //Assert.assertEquals(5, ((MerkleTree)MerkleTreeInstance.getShimInstance()).size());
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(wrapper));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i1));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i2));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(l1));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(l2));
        Assert.assertEquals(-1, MerkleTreeInstance.getShimInstance().getIndex(i3));
    }
}
