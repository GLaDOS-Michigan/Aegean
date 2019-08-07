/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package merkle;

import java.util.BitSet;

/**
 * @author yangwang
 */
class MerkleTreeIndex {
    private BitSet b;

    public MerkleTreeIndex(int maxEntries) {
        b = new BitSet(maxEntries);
    }

    public int getFirstEmptyEntry() {
        return b.nextClearBit(0);
    }

    public void setEmpty(int index) {
        b.clear(index);
    }

    public void setUsed(int index) {
        b.set(index);
    }

    public void clearAll() {
        b.clear();
    }

    public static void main(String[] args) {
        MerkleTreeIndex index = new MerkleTreeIndex(1048576 / 4);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1048576 / 4; i++) {
            int tmp = index.getFirstEmptyEntry();
            index.setUsed(tmp);
        }
        System.out.println("throughput=" + 1048576 / 4 / (System.currentTimeMillis() - startTime));
    }
}
