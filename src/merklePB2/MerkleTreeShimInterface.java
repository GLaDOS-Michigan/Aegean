package merklePB2;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public interface MerkleTreeShimInterface {


    public Object getObject(int index);

    public int getIndex(Object obj);

    /**
     * Get the hash of the whole merkle tree
     *
     * @return: the hash of the merkle tree
     */
    public byte[] getHash();

    /**
     * Change the versionNo of the merkle tree. This function will affect the COW.
     * Any object newer than the versionNo will cause a copy on write.
     *
     * @param versionNo: the new version number of the merkle tree
     */
    public void setVersionNo(long versionNo);

    public void finishThisVersion();

    /**
     * Rollback the merkle tree to a give version number
     *
     * @param versionNo: the version to roll back to.
     */
    public void rollBack(long versionNo) throws MerkleTreeException;

    /**
     * All the states with version number less than versionNo can be garbage collected
     *
     * @param versionNo
     */
    public void makeStable(long versionNo);

    /**
     * Release all states with version number less than stable versionNo
     *
     * @throws MerkleTreeException
     */
    public void gc() throws MerkleTreeException;


    /**
     * Fetch any states with versionNo >startVersion and <=targetVersionNo
     *
     * @param startVersionNo:  the base version number.
     * @param targetVersionNo: the target version number.
     * @return: all states which are newer than versionNo
     */
    public byte[] fetchStates(long startVersionNo, long targetVersionNo) throws MerkleTreeException;

    /**
     * Merge given states into this merkle tree
     *
     * @param versionNo: the version number of the new states
     * @param states:    the state objects
     */
    public void mergeStates(long versionNo, byte[] states) throws MerkleTreeException;

    /**
     * Write all modified objects since last writeLog into a log file.
     *
     * @param out: the output stream to write to.
     * @throws IOException
     */
    public void writeLog(ObjectOutputStream out, long versionNo) throws MerkleTreeException;

    public void readLog(ObjectInputStream in) throws MerkleTreeException;

    /**
     * Write the whole states into a file
     *
     * @param fileName: the file to write to
     * @param seqNo:    the seqNo to write
     * @throws IOException
     */
    public void takeSnapshot(String fileName, long seqNo) throws MerkleTreeException;

    public void loadSnapshot(String fileName) throws MerkleTreeException;

    public int size();

}
