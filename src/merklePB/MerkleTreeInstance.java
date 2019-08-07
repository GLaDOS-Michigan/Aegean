/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package merklePB;

/**
 * @author yangwang
 */
public class MerkleTreeInstance {
    // Singleton mode. Not sure yet, so still leave the public constructor.

    public static MerkleTree tree = null;
    //public static int []removeRecord=new int[1000000];
    //public static int removeRecordIndex=0;

    public static int nbCallsToAdd = 0;
    public static int nbCallsToRemove = 0;
    public static int nbCallsToUpdate = 0;
    public static int nbCallsToLeafHash = 0;
    // public static Vector updatedObject = new Vector();
    public static int nbObjectsUpdated = 0;

    private MerkleTreeInstance() {

    }

    public static void init(int noOfChildren, int noOfObjects, int maxCopies,
                            boolean doParallel) {
        if (BFT.Parameters.useDummyTree)
            tree = new DummyMerkleTree();
        else
            tree = new MerkleTree(noOfObjects, doParallel);
    }

    public static void clear() {
        tree = null;
    }

    public static MerkleTree get() {
        return tree;
    }

    public static MerkleTreeAppInterface getAppInstance() {
        if (tree == null) {
            if (BFT.Parameters.useDummyTree)
                tree = new DummyMerkleTree();
            else
                tree = new MerkleTree();
        }
        return tree;
    }

    public static MerkleTreeShimInterface getShimInstance() {
        if (tree == null) {
            if (BFT.Parameters.useDummyTree)
                tree = new DummyMerkleTree();
            else
                tree = new MerkleTree();
        }
        return tree;
    }

    public static void addRoot(Object obj) {
        try {
            nbCallsToAdd++;
            //System.out.println("Add "+obj.getClass());
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
            //System.out.println("Add "+obj.getClass());
            getAppInstance().add(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void addStatic(Class cls) {
        try {
            nbCallsToAdd++;
            getAppInstance().addStatic(cls);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void update(Object obj) {
        //if(true)
        //    return;
        try {
            boolean found = false;
            // for (Object o : updatedObject)
            // {
            // if (o == obj)
            // {
            // found = true;
            // }
            // }
            // if (!found)
            // {
            // updatedObject.add(obj);
            // }
            nbCallsToUpdate++;
            getAppInstance().update(obj);
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    public static void updateStatic(Class cls) {
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
