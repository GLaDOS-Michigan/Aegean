/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merkle;

import BFT.Parameters;

/**
 * @author yangwang
 */
public class MerkleTreeInstance {
    // Singleton mode. Not sure yet, so still leave the public constructor.

    public static MerkleTree tree = null;

    public static int nbCallsToAdd = 0;
    public static int nbCallsToRemove = 0;
    public static int nbCallsToUpdate = 0;
    public static int nbCallsToLeafHash = 0;
    public static int nbObjectsUpdated = 0;

    private MerkleTreeInstance() {

    }

    public static void init(Parameters param, int noOfChildren, int noOfObjects, int maxCopies,
                            boolean doParallel) {
        if (param.useDummyTree)
        {
            tree = new DummyMerkleTree(param);
        }
        else if(param.normalMode || (param.getExecutionLiars() != 0 && !param.primaryBackup)) {
            System.out.println("We have liars or we are in normal mode so we should use MerkleTree");
            tree = new MerkleTree(param, noOfChildren, noOfObjects, maxCopies, doParallel);
        }
        else {
            System.out.println("no liars, use CFT Merkle Tree");
            param.CFTmode = true;
            tree = new CFTMerkleTree(param, noOfChildren, noOfObjects, maxCopies, doParallel);
        }
    }

    public static void clear() {
        tree = null;
    }

    public static MerkleTree get() {
        return tree;
    }

    public static MerkleTreeAppInterface getAppInstance() {
        return tree;
    }

    public static MerkleTreeShimInterface getShimInstance() {
        return tree;
    }

    public static void addRoot(Object obj) {
        try {
            nbCallsToAdd++;
            getAppInstance().addRoot(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void add(Object obj) {
        if (tree.versionNo > 0) //Using scan instead of add
            return;
        try {
            nbCallsToAdd++;
            getAppInstance().add(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void addStatic(Class<?> cls) {
        try {
            nbCallsToAdd++;
            getAppInstance().addStatic(cls);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void update(Object obj) {
        try {
            nbCallsToUpdate++;
            MerkleTreeAppInterface merkleTree = getAppInstance();
            if(merkleTree != null)
            {
                merkleTree.update(obj);
            }
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void updateStatic(Class<?> cls) {
        try {
            nbCallsToUpdate++;
            getAppInstance().updateStatic(cls);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void remove(Object obj) {
        try {
            nbCallsToRemove++;
            getAppInstance().remove(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }
}
