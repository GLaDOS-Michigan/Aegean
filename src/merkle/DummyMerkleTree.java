package merkle;

import BFT.Parameters;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DummyMerkleTree extends MerkleTree {
    long versionNo = 0;

    public DummyMerkleTree(Parameters param) {
        this(param, 2, 1024, 5, false);
    }

    public DummyMerkleTree(Parameters param, int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {
        System.out.println("Using dummy merkle");
        parameters = param;
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


    public void setRequestID(Comparable<?> Id) {

    }

    public void addRoot(Object object) throws MerkleTreeException {

    }

    public boolean add(Object object) throws MerkleTreeException {
        return true;
    }


    public void finishThisVersion() {

    }


    public void addStatic(Class<?> cls) throws MerkleTreeException {

    }


    public void update(Object object) throws MerkleTreeException {

    }

    public void updateStatic(Class<?> cls) throws MerkleTreeException {

    }

    public void remove(Object object) throws MerkleTreeException {

    }


    //public byte[] ret = new byte[32];
    public byte[] getHash() {
        return new byte[getParameters().digestLength];
    }


    public void setVersionNo(long versionNo) {
	this.versionNo = versionNo;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public void rollBack(long versionNo) throws MerkleTreeException {

    }

    public void makeStable(long versionNo) {

    }

    public void gcIfNeeded() throws MerkleTreeException {

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
