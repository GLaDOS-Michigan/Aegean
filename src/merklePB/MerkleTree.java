package merklePB;

import Applications.tpcw_ev.HierarchicalBitmap;
import BFT.Debug;
import BFT.util.UnsignedTypes;

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

    //private TreeMap<Long, Integer> nextAvailable = new TreeMap<Long, Integer>();
    private HierarchicalBitmap bitmap;

    private int noOfObjects;
    private int mergeFactor;
    long versionNo = 0;
    private long stableVersionNo = -1;
    private ConcurrentHashMap<ObjectWrapper, Integer> mapper;

    public int noOfLeafHashes = 0;
    private final Object sync = new Object();
    private int numOfThreads;
    private int hashWorkFinished;
    private boolean doParallel = false;
    private boolean fakeHash = false;
    private TreeSet<Integer>[] dirtyList;
    private byte[][] hashList;

    private void serializeMetadata(ObjectOutputStream out, long versionNo) throws IOException {
        out.writeLong(versionNo);
    }

    private long deSerializeMetadata(ObjectInputStream in) throws IOException, ClassNotFoundException {
        return in.readLong();
    }

    private class ObjectWrapper {

        private Object object;

        public ObjectWrapper() {
        }

        public ObjectWrapper(Object object) {
            this.object = object;
        }

        public void setObject(Object object) {
            this.object = object;
        }

        @Override
        public int hashCode() {
            return object.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            ObjectWrapper tmp = (ObjectWrapper) obj;
            //System.out.println(this.object == tmp.object);
            //System.out.println(this.object+" == "+tmp.object);
            return this.object == tmp.object;
        }
    }

    public MerkleTree() {
        this(1024, false);
    }

    public MerkleTree(int noOfObjects, boolean doParallel) {
        this.doParallel = doParallel;
        //this.noOfChildren = noOfChildren;
        //this.maxCopies = maxCopies;
        this.noOfObjects = noOfObjects;
        this.mergeFactor = BFT.Parameters.mergeFactor;
        this.currentObjects = new Object[this.noOfObjects * this.mergeFactor];
        this.currentFlags = new byte[this.noOfObjects * this.mergeFactor];
        this.currentVersionNos = new int[this.noOfObjects * this.mergeFactor];
        this.currentCreateVersionNos = new int[this.noOfObjects * this.mergeFactor];


        //this.nextAvailable.put(0L, 0);
        this.bitmap = new HierarchicalBitmap(this.noOfObjects * this.mergeFactor);
        /*for (int i = 0; i < noOfInternalNodes; i++) {
        this.internalNodes[i] = new byte[0];
        }*/
        mapper = new ConcurrentHashMap<ObjectWrapper, Integer>();

        dirtyList = new TreeSet[16];
        hashList = new byte[16][];
        for (int i = 0; i < dirtyList.length; i++)
            dirtyList[i] = new TreeSet<Integer>();

        if (doParallel) {
            numOfThreads = BFT.Parameters.noOfThreads;//reservedTopInternalNodes*(noOfChildren-1)+1;
        }

        for (int i = 0; i < allowAdding.length; i++) {
            allowAdding[i] = true;
        }

        Debug.debug(Debug.MODULE_MERKLE, "MerkleTree initialized"
                        + " noOfObjects=%d, mergeFactor=%d\n",
                noOfObjects, mergeFactor);
        //System.out.println("depth=" + depth + " noOfObjects=" + this.noOfObjects + " noOfInternalNodes=" + noOfInternalNodes);
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
                throw new RuntimeException("MerkleTree is full");
            }
            bitmap.set(ret);
            return ret;
        }
    }

    private void setEmpty(int index) {
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

    //Handling Concurrent Add
    private Comparable[] threadToId = new Comparable[128];

    private int getThreadId() {
        Thread t = Thread.currentThread();
        if (t instanceof IndexedThread) {
            return ((IndexedThread) Thread.currentThread()).getIndex() + 1;
        } else {
            return 0;
        }
    }

    public void setRequestID(Comparable Id) {
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
        int threadId = getThreadId();
        if (!getAllowAdding(threadId)) {
            //MerkleTreeInstance.nbCallsToAdd--;
            return false;
        }
        if (this.getIndex(object) != -1) {
            return false;
        }

        int index = this.getFirstEmptySlot();
    /*if(index>=884600){
	    Throwable t = new Throwable();
	    System.err.println("add "+index+" "+object.getClass()+" "+object);
	    t.printStackTrace();
	}*/
	/*if(versionNo>0){
            System.err.println("add "+object+" to"+index);
        }*/

        // There are possible removed objects in this slot

        new MerkleTreeLeafNode(this.versionNo, this.versionNo, object, false, true).toCurrent(this, index);
        addToMapper(object, index);
        markAsUpdated(index);
        return true;
    }

    public void finishThisVersion() {
        try {
            this.scanNewObjects();
        } catch (MerkleTreeException e) {
            e.printStackTrace();
        }
    }

    private boolean[] allowAdding = new boolean[100];

    private void setAllowAdding(int threadId, boolean allow) {
        allowAdding[threadId] = allow;
    }

    private boolean getAllowAdding(int threadId) {
        return allowAdding[threadId];
    }

    public void addStatic(Class cls) throws MerkleTreeException {
        int threadId = getThreadId();
        if (!getAllowAdding(threadId)) {
            return;
        }
        // System.out.println("AddStatic " + cls);
        if (this.getIndex(cls) != -1) {
            return;
        }

        int index = this.getFirstEmptySlot();

        //System.out.println("Add index=" + index);

        new MerkleTreeLeafNode(this.versionNo, this.versionNo, cls, true, true).toCurrent(this, index);

        addToMapper(cls, index);
        //this.setUsed(index);
        markAsUpdated(index);
        rootIndexes.add(index);
    }

    //private int updatedStatistics = 0;
    //private final Object statLock = new Object();

    private void markAsUpdated(int index) {
        int bucket = index % dirtyList.length;
        synchronized (dirtyList[bucket]) {
            dirtyList[bucket].add(index);
        }
    }

    private void clearDirtyList(boolean clearFlag) {
        for (int i = 0; i < dirtyList.length; i++) {
            if (clearFlag) {
                for (Integer index : dirtyList[i])
                    MerkleTreeLeafNode.setUpdated(this, index, false);
            }
            dirtyList[i].clear();
        }
    }

    public void update(Object object) throws MerkleTreeException {
        //if(versionNo>1)
        //    System.out.println("update "+object);
        //if(!(object instanceof MerkleTreeObject))
        //    System.out.println("Update " + object.getClass());
        int index = getIndex(object);
        if (index == -1) {
            //System.out.println("cannot find "+object);
            return;
            //throw new MerkleTreeException("Object not found");
        }
        if (currentObjects[index] != object) {
            System.err.println("Object not match" + object + " " + currentObjects[index]);
            return;
        }
        try {
            //System.out.println("index=" + index);
            synchronized (currentObjects[index]) {
                if (MerkleTreeLeafNode.isDeleted(this, index)) {
                    throw new MerkleTreeException("Updating a deleted object " + object);
                }
                if (currentObjects[index] != object) {
                    throw new MerkleTreeException("Unmatching object target" + index + "=" + object.getClass() + ":" + object
                            + " current=" + currentObjects[index].getClass() + ":" + currentObjects[index]);
                }

                currentVersionNos[index] = (int) this.versionNo;

                if (MerkleTreeLeafNode.isUpdated(this, index)) {
                    return;
                }
                //System.out.println("No need to copy");
                MerkleTreeLeafNode.setUpdated(this, index, true);
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

    public void updateStatic(Class cls) throws MerkleTreeException {
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

                if (MerkleTreeLeafNode.isUpdated(this, index)) {
                    return;
                }
                //System.out.println("No need to copy");
                MerkleTreeLeafNode.setUpdated(this, index, true);
                currentVersionNos[index] = (int) this.versionNo;
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
        //System.out.println("Update " + object);
        //if(!(object instanceof MerkleTreeObject))
        //    System.out.println("Remove " + object.getClass());
        //System.out.println("Remove " + object);
        int index = getIndex(object);
        if (index == -1) {
            return;
            //throw new MerkleTreeException("Object not found");
        }
        remove(index);
    }

    private static class RemovedTag extends Object {
    }

    ;
    private static RemovedTag removed = new RemovedTag();

    private void remove(int index) throws MerkleTreeException {

        if (MerkleTreeLeafNode.isDeleted(this, index)) {
            //throw new RuntimeException("Deleting a deleted object");
            return;
        }
        synchronized (currentObjects[index]) {
            this.removeFromMapper(currentObjects[index]);
            currentCreateVersionNos[index] = (int) this.versionNo;
            currentVersionNos[index] = (int) this.versionNo;
            currentObjects[index] = removed;
            MerkleTreeLeafNode.setDeleted(this, index);
        }
        this.setEmpty(index);
        markAsUpdated(index);
    }

    //public byte[] ret = new byte[32];

    public byte[] getHash() {
        //System.err.println("getHash for seqNo "+versionNo);
        long startTime = System.nanoTime();
        //if(true) return new byte[BFT.Parameters.digestLength];
        noOfLeafHashes = 0;
        //Debug.debug(Debug.MODULE_MERKLE, "GetHash tree size=%d\n", this.size());
        this.finishThisVersion();
        //System.out.println("time2="+(System.currentTimeMillis()-startTime));
        if (doParallel) {
            synchronized (sync) {
                hashWorkFinished = 0;
            }
            if (MerkleTreeThread.getThreadCount() < numOfThreads) {

                for (int i = MerkleTreeThread.getThreadCount(); i < numOfThreads; i++) {
                    new MerkleTreeThread(this).start();
                }

                assert MerkleTreeThread.getThreadCount() == numOfThreads;
            }


            LinkedList<Integer> hashWorks = new LinkedList<Integer>();

            for (int i = 0; i < dirtyList.length; i++)
                hashWorks.add(i);

	    /*if(versionNo > 0){
		System.err.println("VersionNo = "+versionNo);
	    	for(int i=0;i<dirtyList.length;i++){
		     System.err.println("DirtyList "+i);
		     for(Integer j : dirtyList[i])
			System.err.print(j+" ");
		     System.err.println();
		}
	    }*/

            for (int j = 0; j < numOfThreads; j++) {
                MerkleTreeThread.getAllThreads().get(j).startHashWork(hashWorks);
            }

            /*synchronized (MerkleTreeThread.lock) {
                MerkleTreeThread.lock.notifyAll();
            }*/
            synchronized (sync) {
                while (hashWorkFinished < dirtyList.length) {
                    try {
                        sync.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } else {
            for (int i = 0; i < dirtyList.length; i++) {
                try {
                    getHash(i, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        clearDirtyList(false);
        //System.out.println("parallel="+(System.nanoTime()-startTime));
        //startTime = System.currentTimeMillis();
        try {
            //this.hashWorks.clear();
            //System.out.println("haha="+(System.currentTimeMillis()-startTime)+" "+noOfInternalHashes+" "+noOfLeafHashes);
            Checksum checksum = new Checksum();
            if (!fakeHash) {
                for (int i = 0; i < hashList.length; i++)
                    checksum.update(hashList[i]);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                this.serializeMetadata(oos, versionNo);
                oos.flush();
                checksum.update(bos.toByteArray());
                byte[] ret = checksum.getValue();
                //UnsignedTypes.printHash(ret);
                //printInfo();
                return ret;
            } else {
                return new byte[BFT.Parameters.digestLength];
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //System.out.println("all="+(System.nanoTime()-startTime));
            //System.out.println("leafHashes="+noOfLeafHashes);//+" updatedStatistics="+updatedStatistics);
            noOfLeafHashes = 0;
            Debug.debug(Debug.MODULE_MERKLE, "Gethash %s time=%d\n", Thread.currentThread(), (int) (System.currentTimeMillis() - startTime));
        }
    }

    private static byte[] longToBytes(long value) {
        return new byte[]{
                (byte) ((value >> 56) & 0xff),
                (byte) ((value >> 48) & 0xff),
                (byte) ((value >> 40) & 0xff),
                (byte) ((value >> 32) & 0xff),
                (byte) ((value >> 24) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 0) & 0xff),
        };
    }

    protected byte[] getHash(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        if (fakeHash) {
            hashList[index] = new byte[0];
            return hashList[index];
        }
        Checksum checksum = new Checksum();
        TreeSet<Integer> works = dirtyList[index];
        //if(versionNo>0)
        //    System.err.print(index+":");
        for (Integer i : works) {
            //if(versionNo>0)
            //    System.err.print(i+" ");
            if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i)) {
                checksum.update(new byte[0]);
            } else {
                MerkleTreeLeafNode.setUpdated(this, i, false);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                MerkleTreeLeafNode.write(oos, this, i);
                oos.flush();
                checksum.update(bos.toByteArray());
		/*if(versionNo>0){
		    System.err.println(i+" "+currentObjects[i].getClass()+" "+UnsignedTypes.bytesToHexString(checksum.getValue()));
		    if(currentObjects[i].getClass().getName().endsWith("TreeNode")||currentObjects[i].getClass().getName().endsWith("result.Row"))
			Tools.printObject(currentObjects[i], this);
		}*/
                noOfLeafHashes++;
            }
        }
        //if(versionNo>0)
        //  System.err.println();
        hashList[index] = checksum.getValue();
        //this.internalNodes[index].setUpdated(false);
        return hashList[index];
    }

    protected void finishGetHash() {
        synchronized (sync) {
            //System.out.println("ST: Work done. going to increase counter");
            hashWorkFinished++;
            if (hashWorkFinished == dirtyList.length)
                sync.notifyAll();
        }
    }


    private byte[] getLeafHash1(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        if (fakeHash)
            return new byte[0];
        digest.reset();
        Checksum checksum = new Checksum();
        if (currentObjects[index] == null || MerkleTreeLeafNode.isDeleted(this, index)) {
            return new byte[0];
        } else {
            MerkleTreeLeafNode.setUpdated(this, index, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            MerkleTreeLeafNode.write(oos, this, index);
            oos.flush();
            //Tools.printHash(bos.toByteArray());

            checksum.update(bos.toByteArray());
        }
        //UnsignedTypes.printHash(hash);
        return checksum.getValue();
    }


    public void setVersionNo(long versionNo) {
        Debug.fine(Debug.MODULE_MERKLE, "SetVersion %d currentVersion=%d\n", versionNo, this.versionNo);
        //this.finishThisVersion();
        if (this.versionNo >= versionNo) {
            return;
        }
        this.versionNo = versionNo;
        //System.out.println(mapper.size());
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

    /* For PB, this is just a partial rollback: delete any new objects added after versionNo */
    public void rollBack(long versionNo) throws MerkleTreeException {
        throw new MerkleTreeException("PB merkle tree should use another implementation");
    }

    public int[] pbRollBack(long versionNo) throws MerkleTreeException {
        ArrayList<Integer> ret = new ArrayList<Integer>();
        for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
            if (currentObjects[i] == null) {
                continue;
            }
            if (currentCreateVersionNos[i] > versionNo) {
                //System.out.println("rollback "+i);
                this.setEmpty(i);
                removeFromMapper(currentObjects[i]);
                currentObjects[i] = null;
            } else if (currentVersionNos[i] > versionNo) {
                //System.out.println("fetch "+i);
                ret.add(i);
            }

        }
        this.versionNo = versionNo;
        int[] retArray = new int[ret.size()];
        for (int i = 0; i < ret.size(); i++)
            retArray[i] = ret.get(i);
        return retArray;
    }


    public void makeStable(long versionNo) {
        Debug.fine(Debug.MODULE_MERKLE, "makeStable %d currentVersion=%d stableVersion=%d\n",
                versionNo, this.versionNo, this.stableVersionNo);
        //this.finishThisVersion();
        this.stableVersionNo = versionNo;

    }

    private boolean[] used = null;

    public void gc() throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "MerkleTree GC\n");
        try {
            if (used == null) {
                used = new boolean[this.noOfObjects * this.mergeFactor];
            }
            this.finishThisVersion();
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                used[i] = false;
            }
            LinkedList<Object> toCheck = new LinkedList<Object>();
            for (Integer root : rootIndexes) {
                toCheck.add(currentObjects[root]);
                used[root] = true;
            }

            Object tmp = null;

            Iterator<Object> iter = toCheck.iterator();
            while ((tmp = toCheck.poll()) != null) {
                List<Object> refs = Tools.getAllReferences(tmp);
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
                //iter.remove();
            }
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (used[i] == false && this.isUsed(i)) {
                    //System.out.println("gc removes "+i);
                    this.remove(i);
                }
            }
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    private LinkedList<Object> newObjects = new LinkedList<Object>();
    private int scanCount = 0;

    public void scanNewObjects() throws MerkleTreeException {
	/*System.err.println("Start scan");
	for(int i=0;i<currentObjects.length;i++){
            if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i) || !MerkleTreeLeafNode.isUpdated(this, i)) {
                continue;
            }
            if(versionNo>0)
                System.err.println(i+" is updated");
        }*/

        scanCount = 0;
        findNewObjects();
        Iterator<Object> iter = newObjects.iterator();
        Object tmp = null;
        while ((tmp = newObjects.poll()) != null) {
            this.add(tmp);
            //if(versionNo>0)
            //System.out.println("Find new "+tmp.getClass()+" "+this.getIndex(tmp));
            scanNewObject(tmp);
        }
        newObjects.clear();
	/*for(int i=0;i<currentObjects.length;i++){
            if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i) || !MerkleTreeLeafNode.isUpdated(this, i)) {
                continue;
            }
	    if(versionNo>0)
		System.err.println(i+" is updated");
        }
	System.err.println("End scan");*/
        //System.out.println("scanCount="+scanCount);
    }

    private void scanNewObject(Object obj) throws MerkleTreeException {
        try {
            scanCount++;
            List<Object> refs = Tools.getAllReferences(obj);
            if (refs != null) {
                for (Object tmp : refs) {
                    if (this.getIndex(tmp) < 0) {
                        newObjects.add(tmp);
                        //if(versionNo>1)
                        //    System.err.println("Add "+tmp+" "+System.identityHashCode(tmp)+" parent="+obj);
                    }
                    //else
                    //if(versionNo>1)
                    //    System.err.println(tmp+" "+System.identityHashCode(tmp)+" already in tree "+this.getIndex(tmp));
                }
            }
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    private void findNewObjects() throws MerkleTreeException {
        try {
            for (int i = 0; i < dirtyList.length; i++) {
                for (Integer index : dirtyList[i]) {
                    scanNewObject(currentObjects[index]);
                }
            }
        } catch (MerkleTreeException e) {
            throw e;
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }
    }

    public byte[] fetchStates(long startVersionNo, long targetVersionNo) throws MerkleTreeException {
        //throw new MerkleTreeException("PB should call pbFetchStates");
        return pbFetchStates(startVersionNo, targetVersionNo, new int[0]);
    }

    public byte[] pbFetchStates(long startVersionNo, long targetVersionNo, int[] indexes) throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "fetchStates %d to %d\n", startVersionNo, targetVersionNo);
        this.finishThisVersion();
        HashSet<Integer> indexSet = new HashSet<Integer>();
        for (int i = 0; i < indexes.length; i++)
            indexSet.add(indexes[i]);
        try {
            //System.out.println("Got fetchState "+startVersionNo+" to "+targetVersionNo);
            if (this.stableVersionNo != -1 && this.stableVersionNo > targetVersionNo) {
                Debug.info(Debug.MODULE_MERKLE, "Trying to fetch an old state");
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            this.serializeMetadata(oos, targetVersionNo > versionNo ? versionNo : targetVersionNo);
            for (int i = 0; i < indexes.length; i++) {
                if (currentObjects[indexes[i]] == null) {
                    System.out.println("fetch a null object");
                    continue;
                }
                oos.writeInt(indexes[i]);
                MerkleTreeLeafNode.write(oos, this, indexes[i]);
            }

            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                if (currentVersionNos[i] <= targetVersionNo
                        && currentVersionNos[i] > startVersionNo && !indexSet.contains(i)) {
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

    private void rebuildTransientState() throws Exception {
        for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
            if (currentObjects[i] == null) {
                continue;
            }
            if (currentObjects[i].getClass().getName().endsWith("Database")) {
                System.out.println("Find database " + currentObjects[i].getClass() + " at " + i);
                Method m = currentObjects[i].getClass().getDeclaredMethod("createScanIndices", new Class[0]);
                m.invoke(currentObjects[i], new Object[0]);
		/*Method[] ms = currentObjects[i].getClass().getDeclaredMethods();
		for(int j=0; j<ms.length; j++)
		    System.out.println(ms[j]);*/
            }
        }
    }

    private void printInfo() throws Exception {
        int[] indexes = new int[]{1126, 9437, 9498, 9570, 9622, 9726, 9850, 9912, 9992, 10036, 10086};
        System.err.println("print info for seqNo " + versionNo);
        for (int i = 0; i < indexes.length; i++) {
            if (currentObjects[indexes[i]] == null) {
                continue;
            }
            if (currentObjects[indexes[i]].getClass().getName().endsWith("ScanIndex")) {
                System.err.println("ScanIndex at " + indexes[i]);
                Tools.printObject(currentObjects[indexes[i]], this);
            }
        }
    }

    public void mergeStates(long versionNo, byte[] states) throws MerkleTreeException {
        Debug.info(Debug.MODULE_MERKLE, "mergeStates %d currentVersion=%d stableVersion=%d\n",
                versionNo, this.versionNo, this.stableVersionNo);
        clearDirtyList(true);
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
                //System.out.println("merge "+tmp.getObject().getClass()+" "+index);
                if (currentObjects[index] == null) {
                    //System.out.println("add to empty");
                    tmp.toCurrent(this, index);
                    addToMapper(tmp.getObject(), index);
                } else {
                    if (!MerkleTreeLeafNode.isStatic(this, index)) {
                        //if(tmp.getVersionNo()<=this.objects[index].getFirst().getVersionNo())
                        //    continue;
                        Object current = currentObjects[index];
                        long createSeqNo = currentCreateVersionNos[index];
                        long seqNo = currentVersionNos[index];
                        //if(index==2) System.out.println("load "+index+" seqNo="+tmp.getVersionNo()+" createSeqNo="+tmp.getCreateVersionNo());
                        if (createSeqNo == tmp.getCreateVersionNo() && !MerkleTreeLeafNode.isDeleted(this, index)) {

                            if (tmp.getObject() != null) {
                                //A normal merge
                                //System.out.println("normal merge");
                                Tools.copyObject(tmp.getObject(), current);
                                tmp.setObject(current);
                                //UnsignedTypes.printHash(getLeafHash(index));
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
                            //System.out.println("replace merge");
                        }
                    }

                    tmp.toCurrent(this, index);
                }
                MerkleTreeLeafNode.setUpdated(this, index, false);
                if (currentObjects[index] != null && !MerkleTreeLeafNode.isDeleted(this, index)) {
                    this.setUsed(index);
                } else {
                    this.setEmpty(index);
                }
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
                }
            }
            oos.flush();
        } catch (Exception e) {
            throw new MerkleTreeException(e);
        }

    }

    private void clear() {
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
                //System.out.println("load "+tmp.getIndex());
                toConnect.add(tmp);
                tmp.toCurrent(this, index);
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
        //this.getHash();
        try {
            StringBuilder builder = new StringBuilder();
            MessageDigest digest = MessageDigest.getInstance(BFT.Parameters.digestType);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                builder.append("object[" + i + "]:\n");
                builder.append("\t" + +currentCreateVersionNos[i] + "->" + currentVersionNos[i] + " " + MerkleTreeLeafNode.isDeleted(this, i) + " " + MerkleTreeLeafNode.isStatic(this, i) + ":");
                //builder.append("\t" + tmp.isDeleted() + "\t");
                if (currentObjects[i] != null) {
                    builder.append(UnsignedTypes.bytesToHexString(getLeafHash1(i, digest)) + " " + currentObjects[i].getClass() + " " + currentObjects[i] + "\n");
                } else {
                    builder.append("\n");
                }
                //if (i == 43) {
                //    merkle.Tools.printObject(tmp.getObject(), this);
                //}

                //}
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
            //MessageDigest digest = MessageDigest.getInstance(BFT.Parameters.digestType);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                if (currentObjects[i] != null) {
                    System.err.print("object[" + i + "]:");
                    System.err.println(currentObjects[i].getClass());
                    //System.err.println(currentCreateVersionNos[i]+" "+currentVersionNos[i]+" "+currentFlags[i]+" ");
                    //System.err.println(UnsignedTypes.bytesToHexString(getLeafHash1(i, digest)) + " " + currentObjects[i].getClass());
                    //if(i==208)
                    //	Tools.printObject(currentObjects[i], this);
                } else {
                    System.err.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //System.exit(0);

    }

    public void checkConsistency() {
    }
}
