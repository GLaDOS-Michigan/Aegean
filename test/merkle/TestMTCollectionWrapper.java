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
import merkle.wrapper.MTCollectionWrapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 *
 * @author yangwang
 */
public class TestMTCollectionWrapper extends TestCase {

    @Override
    protected void setUp() throws Exception {
        MerkleTreeInstance.clear();
    }
    
    public void testCollectionWrapper() throws Exception {
        HashSet<Integer> target = new HashSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1=new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.add(i1);
        Integer i2=new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.add(i2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.add(i3);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertFalse(wrapper.contains(789));

    }


    public void testCollectionWrapperSerialization() throws Exception {
        HashSet<Integer> target = new HashSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1=new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.add(i1);
        Integer i2=new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.add(i2);

        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.add(i3);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));



        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTCollectionWrapper wrapper2=(MTCollectionWrapper)MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.contains(123));
        Assert.assertTrue(wrapper2.contains(456));
        Assert.assertTrue(wrapper2.contains(789));
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.contains(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testCollectionWrapperOrder() throws Exception {
        LinkedList<Integer> target = new LinkedList<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, true, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1=new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.add(i1);
        Integer i2=new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.add(i2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.add(i3);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));
        Assert.assertEquals(123, ((LinkedList)wrapper.getObject()).getFirst());
        Assert.assertEquals(789, ((LinkedList)wrapper.getObject()).getLast());
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertFalse(wrapper.contains(789));
        Assert.assertEquals(123, ((LinkedList)wrapper.getObject()).getFirst());
        Assert.assertEquals(456, ((LinkedList)wrapper.getObject()).getLast());

    }

    public void testCollectionWrapperSerializationOrder() throws Exception {
        TreeSet<Integer> target = new TreeSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, true, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        Integer i1=new Integer(123);
        MerkleTreeInstance.add(i1);
        wrapper.add(i1);
        Integer i2=new Integer(456);
        MerkleTreeInstance.add(i2);
        wrapper.add(i2);

        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.add(i3);
        wrapper.add(i3);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));



        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTCollectionWrapper wrapper2=(MTCollectionWrapper)MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.contains(123));
        Assert.assertTrue(wrapper2.contains(456));
        Assert.assertTrue(wrapper2.contains(789));
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.contains(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testCollectionWrapperDS() throws Exception {
        HashSet<Integer> target = new HashSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, false, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        wrapper.add(123);
        wrapper.add(456);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.add(789);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertFalse(wrapper.contains(789));

    }

    public void testCollectionWrapperSerializationDS() throws Exception {
        HashSet<Integer> target = new HashSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, false, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        wrapper.add(123);
        wrapper.add(456);

        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.add(789);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));



        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTCollectionWrapper wrapper2=(MTCollectionWrapper)MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.contains(123));
        Assert.assertTrue(wrapper2.contains(456));
        Assert.assertTrue(wrapper2.contains(789));
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.contains(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testCollectionWrapperDSOrder() throws Exception {
        TreeSet<Integer> target = new TreeSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, true, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        wrapper.add(123);
        wrapper.add(456);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.add(789);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));

        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertFalse(wrapper.contains(789));

    }

    public void testCollectionWrapperSerializationDSOrder() throws Exception {
        TreeSet<Integer> target = new TreeSet<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, true, true);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.add(wrapper);
        wrapper.add(123);
        wrapper.add(456);

        byte[] hash1 = MerkleTreeInstance.getShimInstance().getHash();
        MerkleTreeInstance.getShimInstance().takeSnapshot("tmp/snap",0);
        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        wrapper.add(789);
        MerkleTreeInstance.getShimInstance().writeLog(new ObjectOutputStream(new FileOutputStream("tmp/log1")), 1);
        Assert.assertTrue(wrapper.contains(123));
        Assert.assertTrue(wrapper.contains(456));
        Assert.assertTrue(wrapper.contains(789));



        byte[] hash2 = MerkleTreeInstance.getShimInstance().getHash();

        MerkleTreeInstance.clear();
        MerkleTreeInstance.getShimInstance().loadSnapshot("tmp/snap");
        MerkleTreeInstance.getShimInstance().readLog(new ObjectInputStream(new FileInputStream("tmp/log1")));
        MTCollectionWrapper wrapper2=(MTCollectionWrapper)MerkleTreeInstance.getShimInstance().getObject(0);
        Assert.assertTrue(wrapper2.contains(123));
        Assert.assertTrue(wrapper2.contains(456));
        Assert.assertTrue(wrapper2.contains(789));
        Assert.assertTrue(Arrays.equals(hash2, MerkleTreeInstance.getShimInstance().getHash()));
        MerkleTreeInstance.getShimInstance().rollBack(0);
        Assert.assertFalse(wrapper2.contains(789));
        //System.out.println("hash4===============");
        Assert.assertTrue(Arrays.equals(hash1, MerkleTreeInstance.getShimInstance().getHash()));

    }

    public void testCollectionGC() throws Exception{
        LinkedList<Integer> target = new LinkedList<Integer>();
        MTCollectionWrapper wrapper = new MTCollectionWrapper(target, false, false);
        //System.out.println("Create wrapper "+wrapper);
        MerkleTreeInstance.addRoot(wrapper);
        Integer i1=new Integer(123);
        wrapper.add(i1);
        Integer i2=new Integer(456);
        wrapper.add(i2);


        MerkleTreeInstance.getShimInstance().setVersionNo(1);
        Integer i3=new Integer(789);
        MerkleTreeInstance.getShimInstance().gc();
        //Assert.assertEquals(3, ((MerkleTree)MerkleTreeInstance.getShimInstance()).size());
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(wrapper));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i1));
        Assert.assertNotSame(-1, MerkleTreeInstance.getShimInstance().getIndex(i2));
        Assert.assertEquals(-1, MerkleTreeInstance.getShimInstance().getIndex(i3));
    }
}


