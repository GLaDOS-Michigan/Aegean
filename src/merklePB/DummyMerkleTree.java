package merklePB;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DummyMerkleTree extends MerkleTree {


    public DummyMerkleTree() {
        this(2, 1024, 5, false);
    }

    public DummyMerkleTree(int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {

    }


    public int getIndex(Object obj) {
        return -1;
    }


    public Object getObject(int index) {
        return null;
    }


    public int size() {
        return 0;
    }


    public void setRequestID(Comparable Id) {

    }

    public void addRoot(Object object) throws MerkleTreeException {

    }

    public boolean add(Object object) throws MerkleTreeException {
        return true;
    }


    public void finishThisVersion() {

    }


    public void addStatic(Class cls) throws MerkleTreeException {

    }


    public void update(Object object) throws MerkleTreeException {

    }

    public void updateStatic(Class cls) throws MerkleTreeException {

    }

    public void remove(Object object) throws MerkleTreeException {

    }


    //public byte[] ret = new byte[32];
    public byte[] getHash() {
        return new byte[BFT.Parameters.digestLength];
    }


    public void setVersionNo(long versionNo) {

    }


    public void rollBack(long versionNo) throws MerkleTreeException {

    }

    public void makeStable(long versionNo) {

    }


    public void gc() throws MerkleTreeException {

    }

    public byte[] fetchStates(long startVersionNo, long targetVersionNo) throws MerkleTreeException {
        return null;
    }

    public void mergeStates(long versionNo, byte[] states) throws MerkleTreeException {

    }

    public void writeLog(ObjectOutputStream oos, long versionNo) throws MerkleTreeException {

    }

    public void readLog(ObjectInputStream ois) throws MerkleTreeException {

    }

    public void takeSnapshot(String fileName, long seqNo) throws MerkleTreeException {

    }


    public void loadSnapshot(String fileName) throws MerkleTreeException {

    }


}
