package merklePB;

public interface MerkleTreeAppInterface {
    /**
     * Add an object to the merkle tree as the root.
     * A root object is never garbage collected and should never
     * be removed
     *
     * @param object: the object to adda
     */
    public void addRoot(Object object) throws MerkleTreeException;

    /**
     * Add an object to the merkle tree
     *
     * @param object: the object to adda
     * @return: if successfully added, return true, otherwise return
     * false (maybe the object is already in the tree)
     */
    public boolean add(Object object) throws MerkleTreeException;

    /**
     * Add the state memeber of a class to the merkle tree.
     * Static classes are always considered as root.
     *
     * @param cls: the class to add
     * @throws MerkleTreeException
     */
    public void addStatic(Class cls) throws MerkleTreeException;

    /**
     * Notify the merkle tree that this object is to be modified.
     * This function should be called before modifying an object
     *
     * @param object: the object to be modified
     */
    public void update(Object object) throws MerkleTreeException;

    /**
     * Notify the merkle tree that the static memeber of this class
     * is to be modified. This function should be called before
     * modifying the static memebers.
     *
     * @param cls: the class to modify
     * @throws MerkleTreeException
     */
    public void updateStatic(Class cls) throws MerkleTreeException;

    /**
     * Remove an object from the Merkle tree
     *
     * @param object: the object to remove
     */
    public void remove(Object object) throws MerkleTreeException;

}
