package merkle;

import Applications.benchmark.BenchReply;
import Applications.benchmark.BenchmarkRequest;
import Applications.tpcw_ev.HierarchicalBitmap;
import BFT.Debug;
import BFT.Parameters;
import BFT.util.UnsignedTypes;
import org.apache.commons.javaflow.Continuation;

import java.io.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MerkleTree implements MerkleTreeAppInterface, MerkleTreeShimInterface {

    Object[] currentObjects;
    byte[] currentFlags;
    int[] currentVersionNos;
    int[] currentCreateVersionNos;
    byte[][] objectHashes;

    private LinkedList<MerkleTreeLeafNode>[] oldObjects;
    //private TreeMap<Long, Integer> nextAvailable = new TreeMap<Long, Integer>();
    private HierarchicalBitmap bitmap;
    private byte[][] internalNodes;
    private int noOfChildren;
    // Some top internal nodes will always be considered as dirty.
    // This could avoid all updates locking root.
    private int reservedTopInternalNodes;
    private int noOfObjects;
    private int mergeFactor;
    private int noOfInternalNodes;
    private int depth;
    private int maxCopies;
    long versionNo = 0;
    private long stableVersionNo = -1;
    private ConcurrentHashMap<ObjectWrapper, Integer> mapper;
    public int noOfInternalHashes = 0;
    public int noOfLeafHashes = 0;
    private final Object sync = new Object();
    private Integer numOfThreads;
    private int hashWorkCount;
    private int hashWorkFinished;
    private boolean doParallel = false;
    private boolean fakeHash = false;
    private int[] workloadCount;
    private Object[] workloadCountLocks;
    private final int bucketSize;
    protected Parameters parameters;
    //double totalTime = 0;
    //double count = 0;

    private void serializeMetadata(ObjectOutputStream out, long versionNo) throws IOException {

        out.writeLong(versionNo);
    }

    private long deSerializeMetadata(ObjectInputStream in) throws IOException, ClassNotFoundException {
        return in.readLong();
    }

    private class ObjectWrapper {

        private Object object;

        @SuppressWarnings("unused")
        // This default constructor is not explicitly called,
        // only for reflection constructing.
        private ObjectWrapper() {
        }

        public ObjectWrapper(Object object) {
            this.object = object;
        }

        @Override
        public int hashCode() {
            return object.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            ObjectWrapper tmp = (ObjectWrapper) obj;
            return this.object == tmp.object;
        }
    }

    public MerkleTree() {
        this(new Parameters(), 2, 1024, 5, false);
    }

    @SuppressWarnings("unchecked")
    public MerkleTree(Parameters param, int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {
        this.parameters = param;
        this.doParallel = doParallel;
        this.noOfChildren = noOfChildren;
        this.maxCopies = maxCopies;
        this.depth = (int) (Math.log(noOfObjects) / Math.log(noOfChildren)) + 1;
        this.noOfObjects = (int) Math.pow(this.noOfChildren, this.depth);
        this.mergeFactor = parameters.mergeFactor;
        this.currentObjects = new Object[this.noOfObjects * this.mergeFactor];
        this.currentFlags = new byte[this.noOfObjects * this.mergeFactor];
        this.currentVersionNos = new int[this.noOfObjects * this.mergeFactor];
        this.currentCreateVersionNos = new int[this.noOfObjects * this.mergeFactor];
        this.objectHashes = new byte[this.noOfObjects * this.mergeFactor][];


        this.oldObjects = (LinkedList<MerkleTreeLeafNode>[])
                (new LinkedList[this.noOfObjects * this.mergeFactor]);

        this.bitmap = new HierarchicalBitmap(this.noOfObjects * this.mergeFactor);
        noOfInternalNodes = (this.noOfObjects - 1) / (this.noOfChildren - 1);
        this.internalNodes = new byte[noOfInternalNodes][];
        mapper = new ConcurrentHashMap<ObjectWrapper, Integer>();
        if (noOfChildren == 2) {
            this.reservedTopInternalNodes = 7;
        } else if (noOfChildren == 4) {
            this.reservedTopInternalNodes = 5;
        } else {
            this.reservedTopInternalNodes = 1;
        }

        if (doParallel) {
            numOfThreads = parameters.noOfThreads;

        }

        int workloadCountLength = 1;
        for (int i = 0; i < parameters.loadBalanceDepth; i++) {
            workloadCountLength *= noOfChildren;
        }
        this.workloadCount = new int[workloadCountLength];
        this.workloadCountLocks = new Object[workloadCountLength];
        for (int i = 0; i < workloadCountLength; i++) {
            this.workloadCountLocks[i] = new Object();
        }
        this.bucketSize = this.noOfObjects / workloadCountLength;

        for (int i = 0; i < allowAdding.length; i++) {
            allowAdding[i] = true;
        }

        Debug.debug(Debug.MODULE_MERKLE, "MerkleTree initialized"
                        + " noOfChildren=%d, noOfObjects=%d, depth=%d, noOfInternalNodes=%d mergeFactor=%d\n",
                noOfChildren, this.noOfObjects, depth, noOfInternalNodes, mergeFactor);
    }

    private void increaseWorkloadCount(int index) {
        int bucket = index / this.bucketSize;
        synchronized (workloadCountLocks[bucket]) {
            workloadCount[bucket]++;
        }
    }

    public int getIndex(Object obj) {
        if (obj instanceof MerkleTreeObject) {
            return ((MerkleTreeObject) obj).getMTIdentifier();
        }
        ObjectWrapper wrapper = new ObjectWrapper(obj);
        Integer ret = mapper.get(wrapper);
        if (ret != null) {
            return ret;
        } else {
            return -1;
        }
    }

    private void addToMapper(Object obj, int index) {
        if (obj == null || obj instanceof RemovedTag) {
            return;
        }
        if (!(obj instanceof MerkleTreeObject)) {
            mapper.put(new ObjectWrapper(obj), index);
        } else {
            ((MerkleTreeObject) obj).setMTIdentifier(index);
        }
    }

    private void removeFromMapper(Object obj) {
        if (obj == null || obj instanceof RemovedTag) {
            return;
        }
        if (obj instanceof BFT.exec.ReplyCache.ReplyInfo) {
            throw new RuntimeException("Remove ReplyInfo");
        }
        if (obj instanceof MerkleTreeObject) {
            ((MerkleTreeObject) obj).setMTIdentifier(-1);
        } else {
            //System.out.println(obj + " removed from mapper");
            if (mapper.remove(new ObjectWrapper(obj)) == null) {
                Debug.warning(Debug.MODULE_MERKLE, "Remove an unknown obj %s", obj);
            }
        }
    }

    public Object getObject(int index) {
        if (this.currentObjects[index] != null && !MerkleTreeLeafNode.isDeleted(this, index)) {
            return this.currentObjects[index];
        }
        return null;
    }

    private int getFirstEmptySlot() {
        synchronized (bitmap) {
            int ret = bitmap.nextClearBit(0);
            if (ret == this.noOfObjects * this.mergeFactor) {
                System.out.println("MerkleTree is full! " + ret);
                throw new RuntimeException("MerkleTree is full");
            }
            bitmap.set(ret);
            return ret;
        }
    }

    private void setEmpty(int index) {
        //Debug.debug(Debug.MODULE_MERKLE, "Setting index %d empty", index);
        synchronized (bitmap) {
            bitmap.clear(index);
        }
    }

    private boolean isUsed(int index) {
        return bitmap.get(index);
    }

    private void setUsed(int index) {
        synchronized (bitmap) {
            bitmap.set(index);
        }
    }

    private void clearBitmap() {
        bitmap.clear();
    }

    public int size() {
        return bitmap.cardinality();
    }

    public Parameters getParameters() {
        return parameters;
    }

    private void clearEntry(int index, boolean force) {
        if (currentObjects[index] == null || oldObjects[index] == null) {
            return;
        }
        if (!force && oldObjects[index].size() <= maxCopies - 1) {
            return;
        }
        synchronized (this.currentObjects[index]) {
            Iterator<MerkleTreeLeafNode> iter = this.oldObjects[index].iterator();
            //Always keep the first one <= stableVersionNo and delete all others<=stableVersionNo
            boolean first = true;
            if (this.currentVersionNos[index] <= this.stableVersionNo) {
                first = false;
            }
            while (iter.hasNext()) {
                long v = iter.next().getVersionNo();
                if (v <= this.stableVersionNo) {
                    if (first) {
                        first = false;
                    } else {
                        iter.remove();
                    }
                }
            }
            if (this.oldObjects[index].size() > this.maxCopies - 1) {
                Debug.debug(Debug.MODULE_MERKLE, "Still exceed maxCopies. Not good\n");
                Debug.debug(Debug.MODULE_MERKLE, "Max Copies: %d, Size:%d", this.maxCopies, this.oldObjects[index].size());
                /*for(MerkleTreeLeafNode tmp:this.objects[index]){
                System.out.println(tmp.getVersionNo()+" "+tmp.getObject());
                }*/
            }
            if (this.oldObjects[index].size() == 0) {
                this.oldObjects[index] = null;
            }
        }
    }

    private void removeClearEntry(int index) {
        if (currentObjects[index] == null || oldObjects[index] == null) {
            return;
        }
        synchronized (this.currentObjects[index]) {
            this.oldObjects[index] = null;
        }
    }

    //Handling Concurrent Add
    private Comparable<?>[] threadToId = new Comparable[16];

    private int getThreadId() {
        Thread t = Thread.currentThread();
        if (t instanceof IndexedThread) {
            return ((IndexedThread) Thread.currentThread()).getIndex() + 1;
        } else {
            return 0;
        }
    }

    public void setRequestID(Comparable<?> Id) {
        threadToId[getThreadId()] = Id;
    }

    private LinkedList<Integer> rootIndexes = new LinkedList<Integer>();

    public void addRoot(Object object) throws MerkleTreeException {
        if (!add(object)) {
            return;
        }
        int index = getIndex(object);
        rootIndexes.add(index);
    }

    public boolean add(Object object) throws MerkleTreeException {
//        Class c = object.getClass();
//        System.out.println(c); //It says Lorg.something.Continuation sometimes
//        if( c == org.apache.commons.javaflow.Continuation.class) {
//            System.out.println(((Continuation)object).toString1());
//            //return false;
//        }
//        System.out.println("checked if stack recorder or Continuation");

//        if(object.getClass().isArray() && object.getClass().getComponentType() == Object.class)
//        {
//            System.err.print("");
//        }

        int threadId = getThreadId();
        if (!getAllowAdding(threadId)) {
            return false;
        }
        if (this.getIndex(object) != -1) {
            return false;
        }

        int index = this.getFirstEmptySlot();
//        if( c == org.apache.commons.javaflow.Continuation.class) {
//            System.err.println("Added Class: " + c + ", index: " + index + ", " + ((Continuation)object).toString1());
//            //return false;
//        }
//        else
//        {
//            System.err.println("Added Class: " + c + ", index: " + index);
//        }

        // There are possible removed objects in this slot
        if (this.currentObjects[index] != null) {
            if (this.oldObjects[index] == null) {
                this.oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
            }
            this.oldObjects[index].addFirst(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
        }

        new MerkleTreeLeafNode(this.versionNo, this.versionNo, object, false, true).toCurrent(this, index);
        addToMapper(object, index);
        markAsUpdated(index);
        this.clearEntry(index, false);
        return true;
    }

    public void finishThisVersion(boolean scanRequired) {
        for (int i = this.reservedTopInternalNodes - 1; i >= 0; i--) {
            for (int j = 1; j <= this.noOfChildren; j++) {
                if (this.internalNodes[i * this.noOfChildren + j] == null) {
                    this.internalNodes[i] = null;
                    break;
                }
            }
        }
        if(scanRequired)
        {
            try {
                this.scanNewObjects();
            } catch (MerkleTreeException e) {
                e.printStackTrace();
            }
        }
    }

    //Wrapper function for new finishThisVersion with boolean parameter. This is to provide compatibility for all places
    //which uses old finishThisVersion and I didn't decide yet about the necessity of scanning objects
    public void finishThisVersion() {
        finishThisVersion(true);
    }

    private boolean[] allowAdding = new boolean[300];

    private void setAllowAdding(int threadId, boolean allow) {
        allowAdding[threadId] = allow;
    }

    private boolean getAllowAdding(int threadId) {
        return allowAdding[threadId];
    }

    public void addStatic(Class<?> cls) throws MerkleTreeException {
        Debug.debug(Debug.MODULE_MERKLE, "Adding static object to tree");
        int threadId = getThreadId();
        if (!getAllowAdding(threadId)) {
            return;
        }
        if (this.getIndex(cls) != -1) {
            return;
        }

        int index = this.getFirstEmptySlot();
        new MerkleTreeLeafNode(this.versionNo, this.versionNo, cls, true, true).toCurrent(this, index);
        addToMapper(cls, index);
        markAsUpdated(index);
        rootIndexes.add(index);
    }

    private void markAsUpdated(int index) {
        index = index / mergeFactor;
        increaseWorkloadCount(index);
        int current = index + this.noOfInternalNodes;
        while (current > this.reservedTopInternalNodes) {
            current = (current - 1) / this.noOfChildren;

            if (this.internalNodes[current] == null) {
                break;
            }
            this.internalNodes[current] = null;
        }
    }

    public void update(Object object) throws MerkleTreeException {
        int index = getIndex(object);
        if (index == -1) {
            Debug.debug(Debug.MODULE_MERKLE, "Object not found in MerkleTree");
            return;
        }
        try {
            synchronized (currentObjects[index]) {
                if (MerkleTreeLeafNode.isDeleted(this, index)) {
                    throw new MerkleTreeException("Updating a deleted object " + object);
                }
                if (currentObjects[index] != object) {
                    throw new MerkleTreeException("Unmatching object target" + index + "=" + object.getClass() + ":" + object
                            + " current=" + currentObjects[index].getClass() + ":" + currentObjects[index]);
                }
                if (currentVersionNos[index] == this.versionNo) {
                    // No need to copy
                    if (MerkleTreeLeafNode.isUpdated(this, index)) {
                        return;
                    }
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                } else {
                    int threadId = getThreadId();
                    this.setAllowAdding(threadId, false);
                    long createSeqNo = currentCreateVersionNos[index];
                    long seqNo = currentVersionNos[index];
                    if (oldObjects[index] == null) {
                        oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
                    }
                    oldObjects[index].addFirst(new MerkleTreeLeafNode(createSeqNo, seqNo, Tools.cloneObject(object), false, false));
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                    this.setAllowAdding(threadId, true);
                    currentVersionNos[index] = (int) versionNo;
                    this.clearEntry(index, false);
                }
            }
            markAsUpdated(index);
        } catch (Exception e) {
            if (e instanceof MerkleTreeException) {
                throw (MerkleTreeException) e;
            } else {
                throw new MerkleTreeException(e);
            }
        }
    }

    public void updateStatic(Class<?> cls) throws MerkleTreeException {
        //System.out.println("Update " + object);
        try {
            int index = getIndex(cls);
            if (index == -1) {
                throw new MerkleTreeException("Object not found");
            }
            synchronized (currentObjects[index]) {
                if (!MerkleTreeLeafNode.isStatic(this, index)) {
                    throw new MerkleTreeException("Unmatching object, not static " + index);
                }

                if (currentVersionNos[index] == this.versionNo) {
                    if (MerkleTreeLeafNode.isUpdated(this, index)) {
                        return;
                    }
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                } else {
                    MerkleTreeLeafNode tmp = MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index);
                    tmp.setStaticAsOld(this);
                    if (oldObjects[index] == null) {
                        oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
                    }
                    oldObjects[index].addFirst(tmp);
                    currentVersionNos[index] = (int) versionNo;
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                    this.clearEntry(index, false);
                }
            }
            markAsUpdated(index);

        } catch (Exception e) {
            if (e instanceof MerkleTreeException) {
                throw (MerkleTreeException) e;
            } else {
                throw new MerkleTreeException(e);
            }
        }
    }

    public void remove(Object object) throws MerkleTreeException {
        int index = getIndex(object);
        if (index == -1) {
            return;
        }
        remove(index);
    }

    private static class RemovedTag extends Object {
    }

    ;
    private static RemovedTag removed = new RemovedTag();

    private void remove(int index) throws MerkleTreeException {

        if (MerkleTreeLeafNode.isDeleted(this, index)) {
            return;
        }
        synchronized (currentObjects[index]) {
            this.removeFromMapper(currentObjects[index]);
            if (oldObjects[index] == null) {
                oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
            }

            oldObjects[index].addFirst(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
            currentCreateVersionNos[index] = (int) this.versionNo;
            currentVersionNos[index] = (int) this.versionNo;
            currentObjects[index] = removed;
            MerkleTreeLeafNode.setDeleted(this, index);
            this.clearEntry(index, false);
        }
        this.setEmpty(index);
        markAsUpdated(index);
    }

    /*
    This method should be called in sendNestedRequest and in replyHandler.result which are currently the only outputs from
    the system. This method will be called by each thread for more parallelism and each thread will calculate its current
    hash by including its history hash in CFT mode. Therefore, we can have more hustle-free and efficient handling with outputs.
    The output should not depend on id's.
     */
    public void addOutput(byte[] threadOutput, int threadId) {
        throw new RuntimeException("this method should only be called in CFT mode which uses CFTMerkleTree");
    }

    public byte[] getHash() {
        long startTime = System.currentTimeMillis();
//        System.err.println("doParallel: " + doParallel + ", numThreads: " + numOfThreads);

        noOfInternalHashes = 0;
        noOfLeafHashes = 0;
        //Debug.debug(Debug.MODULE_MERKLE, "GetHash tree size=%d\n", this.size());
        this.finishThisVersion();
        //First calculate the reserved top internal nodes
        for (int i = this.reservedTopInternalNodes - 1; i >= 0; i--) {
            for (int j = 1; j <= this.noOfChildren; j++) {
                if (this.internalNodes[i * this.noOfChildren + j] == null) {
                    this.internalNodes[i] = null;
                    break;
                }
            }
        }
        if (doParallel) {
            if (MerkleTreeThread.getThreadCount() < numOfThreads) {

                for (int i = MerkleTreeThread.getThreadCount(); i < numOfThreads; i++) {
                    new MerkleTreeThread(this).start();
                }

                assert MerkleTreeThread.getThreadCount() == numOfThreads;
            }


            LinkedList<Integer> hashWorks = new LinkedList<Integer>();
            int tmp = 1;
            int startIndex = 0;

            for (int j = 0; j < parameters.loadBalanceDepth; j++) {
                startIndex += tmp;
                tmp *= noOfChildren;
            }

            for (int j = 0; j < tmp; j++) {
                if (internalNodes[startIndex + j] == null)
                    hashWorks.add(startIndex + j);
            }
            synchronized (sync) {
                hashWorkCount = hashWorks.size();
                hashWorkFinished = 0;
            }
            for (int j = 0; j < numOfThreads; j++) {
                MerkleTreeThread.getAllThreads().get(j).startHashWork(hashWorks);
            }
            synchronized (sync) {
                while (hashWorkFinished < hashWorkCount) {
                    try {
                        sync.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(parameters.digestType);
            byte[] hash = getHash(0, digest);
            if (!fakeHash) {
                digest.reset();
                digest.update(hash);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                this.serializeMetadata(oos, versionNo);
                oos.flush();
                digest.update(bos.toByteArray());
                byte[] ret = digest.digest();
                return ret;
            } else {
                return new byte[parameters.digestLength];
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Debug.debug(Debug.MODULE_MERKLE, "Gethash %s time=%d\n", Thread.currentThread(), (int) (System.currentTimeMillis() - startTime));
            //totalTime += (System.currentTimeMillis() - startTime);
            //count++;
            //if(count % 1000 == 0) {
            //    System.err.println("average: " + totalTime/count);
            //}
        }
    }

    protected byte[] getHash(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        if (this.internalNodes[index] != null) {
            return this.internalNodes[index];
        }
        byte[] hash = new byte[0];
        if (!fakeHash) {
            byte[][] hashes = new byte[this.noOfChildren][];
            for (int i = 1; i <= this.noOfChildren; i++) {
                int current = index * this.noOfChildren + i;

                if (current < this.noOfInternalNodes) {
                    hashes[i - 1] = getHash(current, digest);
                } else {
                    hashes[i - 1] = getLeafHash(current - this.noOfInternalNodes, digest);
                }
            }
            digest.reset();
            for (int i = 0; i < this.noOfChildren; i++)
                digest.update(hashes[i]);
            hash = digest.digest();
        } else {
            for (int i = 1; i <= this.noOfChildren; i++) {
                int current = index * this.noOfChildren + i;

                if (current < this.noOfInternalNodes) {
                    getHash(current, digest);
                } else {
                    getLeafHash(current - this.noOfInternalNodes, digest);
                }
            }
        }
        noOfInternalHashes++;
        this.internalNodes[index] = hash;
        if (hash == null) {
            System.out.println("hash is null");
        }
        return hash;
    }

    protected void finishGetHash() {
        synchronized (sync) {
            hashWorkFinished++;
            if (hashWorkFinished == hashWorkCount)
                sync.notifyAll();
        }
    }

    private boolean cacheHash(byte[] data) {
        return true;
    }

    private byte[] getLeafHash(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        if (fakeHash)
            return new byte[0];
        byte[][] hashes = new byte[mergeFactor][];
        for (int i = index * mergeFactor; i < (index + 1) * mergeFactor; i++) {
            MerkleTreeInstance.nbCallsToLeafHash++;
            digest.reset();
            if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i)) {
                hashes[i - index * mergeFactor] = new byte[0];
            } else if (!MerkleTreeLeafNode.isUpdated(this, i)) {
                assert (objectHashes[i] != null);
                hashes[i - index * mergeFactor] = objectHashes[i];
            } else {
                MerkleTreeLeafNode.setUpdated(this, i, false);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                MerkleTreeLeafNode.write(oos, this, i);
                oos.flush();
                byte[] data = bos.toByteArray();
                hashes[i - index * mergeFactor] = digest.digest(data);
                if (cacheHash(data)) {
                    objectHashes[i] = hashes[i - index * mergeFactor];
                } else {
                }
                noOfLeafHashes++;
            }
        }
        for (int i = 0; i < hashes.length; i++) {
            digest.update(hashes[i]);
        }
        byte[] ret = digest.digest();
        return ret;
    }

    private byte[] getLeafHash1(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
//        Object object = currentObjects[index];
//        System.err.println("index: " + index + ", class " + object.getClass());
//        if(object.getClass() == Applications.benchmark.BenchmarkRequest.class) {
//            BenchmarkRequest br = (BenchmarkRequest) object;
//            System.err.println(br.toString());
//        } else if(object.getClass() == Applications.benchmark.BenchReply.class) {
//            BenchReply br = (BenchReply) object;
//            System.err.println(br.toString());
//        }

//        if(object.getClass().isArray() && object.getClass().getComponentType() == Object.class)
//        {
//            System.err.print("");
//        }

//        if(object.getClass() == org.apache.commons.javaflow.Continuation.class) {
//            System.err.println("STACK CONTENT:");
//            Continuation c = (Continuation) object;
//            System.err.println(c.toString1());
//        }

//        System.err.println();
        if (fakeHash)
            return new byte[0];
        digest.reset();
        if (currentObjects[index] == null || MerkleTreeLeafNode.isDeleted(this, index)) {
            return new byte[0];
        } else {
            MerkleTreeLeafNode.setUpdated(this, index, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            MerkleTreeLeafNode.write(oos, this, index);
            oos.flush();
            //Tools.printHash(bos.toByteArray());
//            System.err.println("bos byte array: " + Arrays.toString(bos.toByteArray()));
            digest.update(bos.toByteArray());
        }
        //UnsignedTypes.printHash(hash);
        return digest.digest();
    }

    public byte[] getLeafHash2(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        Object object = currentObjects[index];

        return getLeafHash1(index, digest);
    }


    public void setVersionNo(long versionNo) {
        Debug.fine(Debug.MODULE_MERKLE, "SetVersion %d currentVersion=%d\n", versionNo, this.versionNo);
        //this.finishThisVersion();
        if (this.versionNo >= versionNo) {
            return;
        }
        this.versionNo = versionNo;
    }

    public long getVersionNo() {
        return versionNo;
    }

    private void connectAll(ArrayList<MerkleTreeLeafNode> toConnect, List<List<Object>> refs) {
        try {
            Iterator<List<Object>> iter = refs.iterator();
            for (MerkleTreeLeafNode obj : toConnect) {
                obj.connectObject(this, iter.next());
                iter.remove();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void rebuildTransientState() throws Exception {
        if (currentObjects[1032] != null) {
            if (currentObjects[1032].getClass().getName().endsWith("Database")) {
                System.out.println("Find database " + currentObjects[1032].getClass() + " at " + 1032);
                Method m = currentObjects[1032].getClass().getDeclaredMethod("createScanIndices", new Class[0]);
                m.invoke(currentObjects[1032], new Object[0]);
            }
        }
    }

    public void rollBack(long versionNo) throws MerkleTreeException {
        int threadId = getThreadId();
        this.setAllowAdding(threadId, false);
        Debug.info(Debug.MODULE_MERKLE, "rollBack to %d, currentVersion=%d, stableVersion=%d\n", versionNo, this.versionNo, this.stableVersionNo);


        if (versionNo < this.stableVersionNo) {
            throw new MerkleTreeException("Cannot rollback before a stable version " + stableVersionNo);
        }

        // Setting versionNo to that of the rollback?
        this.versionNo = versionNo;


        for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
            if (currentObjects[i] == null) {
                continue;
            }

            if (currentVersionNos[i] <= versionNo) {
                continue;
            }

            if (oldObjects[i] == null) {
                oldObjects[i] = new LinkedList<MerkleTreeLeafNode>();
            }

            oldObjects[i].addFirst(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, i));
            long createSeqNo = currentCreateVersionNos[i];
            Object current = currentObjects[i];
            Iterator<MerkleTreeLeafNode> iter = this.oldObjects[i].iterator();

            while (iter.hasNext()) {
                MerkleTreeLeafNode tmp = iter.next();
                if (tmp.getCreateVersionNo() < createSeqNo) {
                    this.removeFromMapper(current);
                    createSeqNo = tmp.getCreateVersionNo();
                    current = tmp.getObject();
                    this.addToMapper(current, i);
                }
                if (tmp.getVersionNo() > versionNo) {
                    iter.remove();
                } else {
                    break;
                }
            }

            try {
                if (oldObjects[i].size() > 0) {
                    if (!oldObjects[i].getFirst().isStatic()) {
                        if (oldObjects[i].getFirst().getObject() != current) {
                            if (oldObjects[i].getFirst().getCreateVersionNo() != createSeqNo || current == null) {
                                throw new MerkleTreeException("Something weird happens");
                            }
                            Tools.copyObject(oldObjects[i].getFirst().getObject(), current);
                            oldObjects[i].getFirst().setObject(current);
                        }

                    } else {
                        if (!oldObjects[i].getFirst().isCurrent()) {
                            oldObjects[i].getFirst().setStaticAsCurrent(this);
                        }
                    }
                    oldObjects[i].getFirst().setUpdated(true);
                    oldObjects[i].removeFirst().toCurrent(this, i);

                    if (oldObjects[i].size() == 0) {
                        oldObjects[i] = null;
                    }

                } else {
                    MerkleTreeLeafNode.setUpdated(this, i, true);
                    currentObjects[i] = null;
                    oldObjects[i] = null;
                    objectHashes[i] = null;
                    removeFromMapper(current);
                }
                if (currentObjects[i] != null && !MerkleTreeLeafNode.isDeleted(this, i)) {
                    this.setUsed(i);
                } else {
                    this.setEmpty(i);
                }
                this.markAsUpdated(i);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            rebuildTransientState();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.setAllowAdding(threadId, true);
    }

    public void makeStable(long versionNo) {
        Debug.fine(Debug.MODULE_MERKLE, "makeStable %d currentVersion=%d stableVersion=%d\n",
                versionNo, this.versionNo, this.stableVersionNo);
        this.finishThisVersion(false);
        this.stableVersionNo = versionNo;

    }

    private boolean[] used = null;

    public void gcIfNeeded() throws MerkleTreeException {
        Debug.debug(Debug.MODULE_MERKLE, "[AM] Calling gcIfNeeded()");
        synchronized (bitmap) {
            int ret = bitmap.nextClearBit(0);
            if (ret >= this.noOfObjects * this.mergeFactor - (this.noOfObjects / (this.noOfChildren / 2))) {
                Debug.warning(Debug.MODULE_MERKLE, "GLOBE calling gc! %d %d %d\n", ret, this.noOfObjects, this.noOfChildren);
                gc();
            }
        }
    }

    public void gc() throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "MerkleTree GC\n");
        try {
            if (used == null) {
                used = new boolean[this.noOfObjects * this.mergeFactor];
            }
            //this.finishThisVersion();
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                used[i] = false;
            }
            LinkedList<Object> toCheck = new LinkedList<Object>();
            for (Integer root : rootIndexes) {
                toCheck.add(currentObjects[root]);
                used[root] = true;
            }
            Object tmp = null;

            while ((tmp = toCheck.poll()) != null) {
                List<Object> refs = Tools.getAllReferences(tmp);
                if(refs ==null)
                    continue;
                for (Object obj : refs) {
                    if (obj == null) {
                        continue;
                    }
                    int index = this.getIndex(obj);
                    if (index < 0) {
                        throw new RuntimeException("Find objects not in the tree " + obj);
                    }
                    if (used[index] == false) {
                        used[index] = true;
                        toCheck.add(obj);
                    }
                }
            }
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (used[i] == false && this.isUsed(i)) {
                    //System.out.println("gc removes "+i);
                    this.remove(i);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new MerkleTreeException(e);
        }
    }

    private LinkedList<Object> newObjects = new LinkedList<Object>();

    public void scanNewObjects() throws MerkleTreeException {

        findNewObjects(0);
        Object tmp = null;
        while ((tmp = newObjects.poll()) != null) {
            this.add(tmp);
            scanNewObject(tmp);
        }
        newObjects.clear();
    }

    private void scanNewObject(Object obj) throws MerkleTreeException {
        try {
            List<Object> refs = Tools.getAllReferences(obj);
            if (refs != null) {
                for (Object tmp : refs) {
                    if (this.getIndex(tmp) < 0) {
//                        Class c = tmp.getClass();
//                        if( c == org.apache.commons.javaflow.Continuation.class) {
//                            if( c == org.apache.commons.javaflow.Continuation.class) {
//                                System.err.println("Scanned Class: " + c + ", " + ((Continuation)tmp).toString1());
//                                //return false;
//                            }
//                        }
                        newObjects.add(tmp);
                    }
                }
            }
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    private void findNewObjects(int index) throws MerkleTreeException {
        try {
            if (index < this.noOfInternalNodes) {
                if (this.internalNodes[index] != null) {
                    return;
                }
                for (int i = 1; i <= this.noOfChildren; i++) {
                    int current = index * this.noOfChildren + i;
                    findNewObjects(current);
                }
            } else {
                index -= this.noOfInternalNodes;
                for (int i = index * mergeFactor; i < (index + 1) * mergeFactor; i++) {
                    if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i) || !MerkleTreeLeafNode.isUpdated(this, i)) {
                        continue;
                    }
                    scanNewObject(currentObjects[i]);
                }
            }
        } catch (MerkleTreeException e) {
            throw e;
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    public byte[] fetchStates(long startVersionNo, long targetVersionNo) throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "fetchStates %d to %d\n", startVersionNo, targetVersionNo);
        this.finishThisVersion();
        try {
            if (this.stableVersionNo != -1 && this.stableVersionNo > targetVersionNo) {
                Debug.info(Debug.MODULE_MERKLE, "Trying to fetch an old state");
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            this.serializeMetadata(oos, targetVersionNo > versionNo ? versionNo : targetVersionNo);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }

                if (oldObjects[i] != null) {
                    synchronized (oldObjects[i]) {
                        Iterator<MerkleTreeLeafNode> iter = oldObjects[i].descendingIterator();
                        while (iter.hasNext()) {
                            MerkleTreeLeafNode tmp = iter.next();
                            if (tmp.getVersionNo() <= targetVersionNo && tmp.getVersionNo() > startVersionNo) {
                                oos.writeInt(i);
                                tmp.write(oos, this);
                            }
                        }
                    }
                }
                if (currentVersionNos[i] <= targetVersionNo
                        && currentVersionNos[i] > startVersionNo) {
                    oos.writeInt(i);
                    MerkleTreeLeafNode.write(oos, this, i);
                }
            }
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    public void mergeStates(long versionNo, byte[] states) throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "mergeStates %d currentVersion=%d stableVersion=%d\n",
                versionNo, this.versionNo, this.stableVersionNo);
        int threadId = getThreadId();
        if (versionNo < this.versionNo) {
            return;
        }
        this.finishThisVersion();
        try {

            this.setAllowAdding(threadId, false);
            ArrayList<MerkleTreeLeafNode> toConnect = new ArrayList<MerkleTreeLeafNode>();
            ArrayList<List<Object>> refs = new ArrayList<List<Object>>();
            ByteArrayInputStream bis = new ByteArrayInputStream(states);
            ObjectInputStream ois = new ObjectInputStream(bis);
            this.deSerializeMetadata(ois);
            while (ois.available() > 0) {
                MerkleTreeLeafNode tmp = new MerkleTreeLeafNode();
                int index = ois.readInt();
                refs.add(tmp.read(ois));

                toConnect.add(tmp);
                if (currentObjects[index] == null) {
                    tmp.toCurrent(this, index);
                    addToMapper(tmp.getObject(), index);
                } else {
                    if (tmp.getVersionNo() <= this.versionNo) {
                        Debug.debug(Debug.MODULE_MERKLE, "Discard %d %d\n", tmp.getVersionNo(), this.versionNo);
                        continue;
                    }
                    if (oldObjects[index] == null) {
                        oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
                    }
                    if (!MerkleTreeLeafNode.isStatic(this, index)) {
                        Object current = currentObjects[index];
                        long createSeqNo = currentCreateVersionNos[index];
                        long seqNo = currentVersionNos[index];
                        if (createSeqNo == tmp.getCreateVersionNo() && !MerkleTreeLeafNode.isDeleted(this, index)) {

                            oldObjects[index].addFirst(new MerkleTreeLeafNode(createSeqNo, seqNo, Tools.cloneObject(current), false, false));
                            if (tmp.getObject() != null) {
                                //A normal merge
                                Tools.copyObject(tmp.getObject(), current);
                                tmp.setObject(current);
                            } else {
                                //Merge a delete
                                this.removeFromMapper(current);
                            }
                        } else {
                            if (currentObjects[index] != null) {
                                this.removeFromMapper(currentObjects[index]);
                            }
                            if (tmp.getObject() != null) {
                                this.addToMapper(tmp.getObject(), index);
                            }
                            oldObjects[index].addFirst(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
                        }
                    } else {
                        MerkleTreeLeafNode.setStaticAsOld(this, index);
                        oldObjects[index].add(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
                    }
                    tmp.toCurrent(this, index);
                    this.clearEntry(index, true);
                }
                if (currentObjects[index] != null && !MerkleTreeLeafNode.isDeleted(this, index)) {
                    this.setUsed(index);
                } else {
                    this.setEmpty(index);
                }
                this.markAsUpdated(index);
            }
            this.connectAll(toConnect, refs);
            this.versionNo = versionNo;
            rebuildTransientState();
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        } finally {
            this.setAllowAdding(threadId, true);
        }
    }

    public void writeLog(ObjectOutputStream oos, long versionNo) throws MerkleTreeException {
        this.finishThisVersion();
        try {
            oos.writeLong(versionNo);
            byte[] data = this.fetchStates(versionNo - 1, versionNo);
            oos.writeObject(data);
            oos.flush();
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    public void readLog(ObjectInputStream ois) throws MerkleTreeException {
        this.finishThisVersion();
        try {
            long v = ois.readLong();
            byte[] data = (byte[]) ois.readObject();
            this.mergeStates(v, data);

        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    public void takeSnapshot(String fileName, long seqNo) throws MerkleTreeException {
        this.finishThisVersion();
        try {
            FileOutputStream fos = new FileOutputStream(new File(fileName));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            this.serializeMetadata(oos, seqNo);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {

                if (currentObjects[i] == null) {
                    continue;
                }
                synchronized (currentObjects[i]) {
                    if (currentVersionNos[i] <= seqNo) {
                        oos.writeInt(i);
                        MerkleTreeLeafNode.write(oos, this, i);
                    }
                    if (oldObjects[i] != null) {
                        Iterator<MerkleTreeLeafNode> iter = this.oldObjects[i].iterator();
                        while (iter.hasNext()) {
                            MerkleTreeLeafNode tmp = iter.next();
                            if (tmp.getVersionNo() <= seqNo) {
                                oos.writeInt(i);
                                tmp.write(oos, this);
                                break;
                            }
                        }
                    }
                }
            }
            oos.flush();
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }

    }

    private void clear() {
        for (int i = 0; i < this.noOfInternalNodes; i++) {
            this.internalNodes[i] = null;
        }
        for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
            this.currentObjects[i] = null;
            this.oldObjects[i] = null;
        }

        mapper.clear();
        this.clearBitmap();
    }

    public void loadSnapshot(String fileName) throws MerkleTreeException {
        this.finishThisVersion();
        int threadId = getThreadId();
        try {
            this.setAllowAdding(threadId, false);
            this.clear();
            FileInputStream fis = new FileInputStream(new File(fileName));
            ObjectInputStream ois = new ObjectInputStream(fis);
            this.versionNo = this.deSerializeMetadata(ois);
            ArrayList<MerkleTreeLeafNode> toConnect = new ArrayList<MerkleTreeLeafNode>();
            ArrayList<List<Object>> refs = new ArrayList<List<Object>>();
            while (ois.available() > 0) {
                MerkleTreeLeafNode tmp = new MerkleTreeLeafNode();
                int index = ois.readInt();
                refs.add(tmp.read(ois));
                toConnect.add(tmp);
                if (currentObjects[index] == null) {
                    tmp.toCurrent(this, index);
                } else {
                    if (oldObjects[index] == null) {
                        oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
                    }
                    oldObjects[index].addLast(tmp);
                }
                this.markAsUpdated(index);
            }
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] != null && !MerkleTreeLeafNode.isDeleted(this, i)) {
                    addToMapper(currentObjects[i], i);
                    this.setUsed(i);
                } else {
                    this.setEmpty(i);
                }
            }
            this.connectAll(toConnect, refs);
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        } finally {
            this.setAllowAdding(threadId, true);
        }
    }

    @Override
    public String toString() {
        try {
            StringBuilder builder = new StringBuilder();
            MessageDigest digest = MessageDigest.getInstance(parameters.digestType);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                builder.append("object[" + i + "]:\n");
                builder.append("\t" + +currentCreateVersionNos[i] + "->" + currentVersionNos[i] + " " + MerkleTreeLeafNode.isDeleted(this, i) + " " + MerkleTreeLeafNode.isStatic(this, i) + ":");
                if (currentObjects[i] != null) {
                    builder.append(UnsignedTypes.bytesToHexString(getLeafHash1(i, digest)) + " " + currentObjects[i].getClass() + " " + currentObjects[i] + "\n");
                } else {
                    builder.append("\n");
                }
                if (oldObjects[i] != null) {
                    for (MerkleTreeLeafNode t : oldObjects[i]) {
                        builder.append("\t" + +t.getCreateVersionNo() + "->" + t.getVersionNo() + " " + t.isDeleted() + ":");
                        if (t.getObject() != null) {
                            builder.append(" " + t.getObject().getClass() + " " + t.getObject() + "\n");
                        } else {
                            builder.append("\n");
                        }
                    }
                }
            }
            return builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void printTree() {
        this.getHash();
        try {
            MessageDigest digest = MessageDigest.getInstance(parameters.digestType);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                System.err.print("object[" + i + "]:");
                if (currentObjects[i] != null) {
                    System.err.print(currentCreateVersionNos[i] + " " + currentVersionNos[i] + " " + currentFlags[i] + " ");
                    System.err.println(UnsignedTypes.bytesToHexString(getLeafHash2(i, digest)) + " " + currentObjects[i].getClass());
                    if (currentObjects[i].getClass().getName().endsWith("Row"))
                        Tools.printObject(currentObjects[i], this);
                } else {
                    System.err.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void checkConsistency() {
    }

    public byte[] pbFetchStates(long startVersionNo, long targetVersionNo, int[] indexes) throws MerkleTreeException {
        throw new MerkleTreeException("This should not be called in EVE");
    }

    public int[] pbRollBack(long versionNo) throws MerkleTreeException {
        throw new MerkleTreeException("This should not be called in EVE");
    }
}
