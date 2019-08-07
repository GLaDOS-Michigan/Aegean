/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;

import BFT.Parameters;

/**
 * @author yangwang
 */
public class MerkleTreeExecInstance {
    //Singleton mode. Not sure yet, so still leave the public constructor.
    private static MerkleTree tree = null;

    private MerkleTreeExecInstance() {

    }

    public static void init(Parameters param, int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {
        tree = new MerkleTree(param, noOfChildren, noOfObjects, maxCopies, doParallel);
    }

    public static void clear() {
        tree = null;
    }

    public static MerkleTreeAppInterface getAppInstance() {
        if (tree == null) {
            tree = new MerkleTree();
        }
        return tree;
    }

    public static MerkleTreeShimInterface getShimInstance() {
        if (tree == null) {
            tree = new MerkleTree();
        }
        return tree;
    }

    public static void add(Object obj) {
        try {
            getAppInstance().add(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void addStatic(Class<?> cls) {
        try {
            getAppInstance().addStatic(cls);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void update(Object obj) {
        try {
            getAppInstance().update(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void updateStatic(Class<?> cls) {
        try {
            getAppInstance().updateStatic(cls);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void remove(Object obj) {
        try {
            getAppInstance().remove(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }
}
