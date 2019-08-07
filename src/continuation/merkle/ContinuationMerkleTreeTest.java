package continuation.merkle;

import BFT.Parameters;
import merkle.MerkleTreeException;
import merkle.MerkleTreeInstance;
import merkle.wrapper.MTCollectionWrapper;
import org.apache.commons.javaflow.Continuation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

class TestRunnable implements Runnable, Serializable {
    MTCollectionWrapper numbers = new MTCollectionWrapper(new ArrayList<Integer>(), true, true);
    transient DataItem dataItem = new DataItem();

    @Override
    public void run() {
        for (int i = 0; i < 10; i++) {
            try {
                MerkleTreeInstance.get().update(numbers);
                MerkleTreeInstance.get().update(dataItem);
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }
            numbers.add(i);
            dataItem.intValue++;
            // the value in dataItem should not be rollback.
            System.out.println("i = " + i + " size = " + numbers.size() + " value = " + dataItem.intValue);
            assert (i + 1 == numbers.size());
            Continuation.suspend();
        }
    }
}

class DataItem {
    public int intValue;
    public long longValue;
}

public class ContinuationMerkleTreeTest {
    private static boolean needRollback;

    public static void main(String[] args) throws MerkleTreeException {
        Random random = new Random();

        Continuation previousContinuation;
        Continuation currentContinuation;

        TestRunnable r = new TestRunnable();
        previousContinuation = currentContinuation = Continuation.startSuspendedWith(r);

        Parameters parameters = new Parameters();
        MerkleTreeInstance.init(parameters, 8, parameters.noOfObjects, 3, parameters.parallelExecution);

        int versionNumber = 0;
        int stableVersionNumber = 0;

        MerkleTreeInstance.addRoot(previousContinuation);
        MerkleTreeInstance.addRoot(currentContinuation);

        MerkleTreeInstance.get().finishThisVersion();

        while (currentContinuation != null) {
            if (needRollback) {
                System.out.println("rolling back.");
                needRollback = false;

                // rollback merkle tree.
                try {
                    MerkleTreeInstance.get().rollBack(stableVersionNumber);
                    currentContinuation = previousContinuation;
                } catch (MerkleTreeException e) {
                    e.printStackTrace();
                }
            } else {
                if (random.nextFloat() > 0.5) {
                    needRollback = true;
                }
                MerkleTreeInstance.get().makeStable(stableVersionNumber);
                stableVersionNumber = versionNumber;
                versionNumber++;
            }

            MerkleTreeInstance.get().setVersionNo(versionNumber);

            MerkleTreeInstance.update(previousContinuation);
            MerkleTreeInstance.update(currentContinuation);

            previousContinuation = currentContinuation;
            currentContinuation = Continuation.continueWith(previousContinuation);

            MerkleTreeInstance.get().finishThisVersion();

            System.out.println("root hash: " + javax.xml.bind.DatatypeConverter.printHexBinary(MerkleTreeInstance.get().getHash()));
//			MerkleTreeInstance.get().printTree();
        }
    }
}
