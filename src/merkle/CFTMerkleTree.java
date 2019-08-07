package merkle;

import BFT.Parameters;
import BFT.exec.ExecBaseNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
This class just extends normal Merkel Tree and just changes hash related things. If the performance still suffers from original
methods, I will override other methods as well.
 */
public class CFTMerkleTree extends DummyMerkleTree {

    byte[][] outputBytesForThreads; //Each thread will add hash of its output to this array when it hit the wall. And getHash
    MessageDigest[] digests;
    //method will take hash of the state by using this array and historyHash.
    //byte[] historyHash; //instead of keeping history hash, I decided to change addOutput method for a better implementation
                          //check this method for explanations

    //TODO needs constructor(s), assert crash failure is zero or primary backup
    public CFTMerkleTree(Parameters param, int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {
        super(param, noOfChildren, noOfObjects, maxCopies, doParallel);
        System.out.println("param.noOfThreads: " + param.noOfThreads );
        outputBytesForThreads = new byte[param.noOfThreads][];
        digests = new MessageDigest[param.noOfThreads+1];

        for(int i = 0; i < outputBytesForThreads.length; i++) {
            outputBytesForThreads[i] = new byte[parameters.digestLength];
            try {
                digests[i] = MessageDigest.getInstance(parameters.digestType);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        try {
            digests[param.noOfThreads] = MessageDigest.getInstance(parameters.digestType);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        assert(param.primaryBackup || param.getExecutionLiars() == 0);
    }

    /*
    This method should be called in sendNestedRequest and in replyHandler.result which are currently the only outputs from
    the system. This method will be called by each thread for more parallelism and each thread will calculate its current
    hash by including its history hash. Therefore, we can have more hustle-free and efficient handling with outputs. The
    output should not depend on id's.
     */
    @Override
    public void addOutput(byte[] threadOutput, int threadId) {
//        System.out.println("add output in CFT mode");
        int numThreads = outputBytesForThreads.length;
        synchronized (outputBytesForThreads[threadId % numThreads]) {
            byte[] historyHashOfTheThread = outputBytesForThreads[threadId % numThreads];
            byte[] finalOutput =  concatenateByteArray(historyHashOfTheThread, threadOutput);
            finalOutput = digests[threadId % numThreads].digest(finalOutput);
            outputBytesForThreads[threadId % numThreads] = finalOutput;
            assert (finalOutput.length == parameters.digestLength);
        }
    }

    public byte[] concatenateByteArray(byte[] a, byte[] b) {
            byte[] c = new byte[a.length + b.length];
            System.arraycopy(a, 0, c, 0, a.length);
            System.arraycopy(b, 0, c, a.length, b.length);
            return c;
    }

    @Override
    public byte[] getHash() {
//        System.out.println("get hash in CFT mode");
        byte[] outputStream = new byte[outputBytesForThreads.length * parameters.digestLength];
        int offset = 0;
        for(int i = 0; i < outputBytesForThreads.length; i++) {
            for(int j = 0; j < parameters.digestLength; j++, offset++) {
                outputStream[offset] = outputBytesForThreads[i][j];
            }
        }
        assert (offset == outputStream.length);
        byte[] finalHash = digests[parameters.noOfThreads].digest(outputStream);
        return finalHash;
        //TODO check which implementation is more efficient or try another way
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(outputBytesForThreads.length * parameters.digestLength);
//        for(int i = 0; i < outputBytesForThreads.length; i++) {
//            try {
//                outputStream.write(outputBytesForThreads[i]);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        byte[] finalHash = digests[parameters.noOfThreads].digest(outputStream.toByteArray());
//        return finalHash;
    }
}

