package merklePB2;

import Applications.tpcw_ev.HierarchicalBitmap;
import BFT.Debug;
import BFT.Parameters;
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

    private LinkedList<MerkleTreeLeafNode>[] oldObjects;
    //private TreeMap<Long, Integer> nextAvailable = new TreeMap<Long, Integer>();
    private HierarchicalBitmap bitmap;

    private int noOfObjects;
    private int mergeFactor;
    private int maxCopies;
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
    private Parameters parameters;

    private void serializeMetadata(ObjectOutputStream out, long versionNo) throws IOException {

        out.writeLong(versionNo);
        //out.writeLong(targetVersionNo);
        /*for (long i = startVersionNo + 1; i <= targetVersionNo; i++) {
        if (!nextAvailable.containsKey(i)) {
        throw new RuntimeException("Unknown versionNo " + i + " stableVersion=" + stableVersionNo);
        }
        out.writeInt(nextAvailable.get(i));
        }    */
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
        this(new Parameters(), 2, 1024, 5, false);
    }

    public MerkleTree(Parameters param, int noOfChildren, int noOfObjects, int maxCopies, boolean doParallel) {
        this.parameters = param;
        this.doParallel = doParallel;
        //this.noOfChildren = noOfChildren;
        this.maxCopies = maxCopies;
        //this.depth = (int) (Math.log(noOfObjects) / Math.log(noOfChildren)) + 1;

        this.noOfObjects = noOfObjects;
        this.mergeFactor = parameters.mergeFactor;
        this.currentObjects = new Object[this.noOfObjects * this.mergeFactor];
        this.currentFlags = new byte[this.noOfObjects * this.mergeFactor];
        this.currentVersionNos = new int[this.noOfObjects * this.mergeFactor];
        this.currentCreateVersionNos = new int[this.noOfObjects * this.mergeFactor];

        this.oldObjects = (LinkedList<MerkleTreeLeafNode>[]) (new LinkedList[this.noOfObjects * this.mergeFactor]);
        //this.nextAvailable.put(0L, 0);
        this.bitmap = new HierarchicalBitmap(this.noOfObjects * this.mergeFactor);
        mapper = new ConcurrentHashMap<ObjectWrapper, Integer>();

        dirtyList = new TreeSet[16];
        hashList = new byte[16][];
        for (int i = 0; i < dirtyList.length; i++)
            dirtyList[i] = new TreeSet<Integer>();

        if (doParallel) {
            numOfThreads = parameters.noOfThreads;//reservedTopInternalNodes*(noOfChildren-1)+1;
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
                if (iter.next().getVersionNo() <= this.stableVersionNo) {
                    if (first) {
                        first = false;
                    } else {
                        iter.remove();
                    }
                }
            }
            if (this.oldObjects[index].size() > this.maxCopies - 1) {
                Debug.debug(Debug.MODULE_MERKLE, "Still exceed maxCopies. Not good\n");
                /*for(MerkleTreeLeafNode tmp:this.objects[index]){
                System.out.println(tmp.getVersionNo()+" "+tmp.getObject());
                }*/
            }
            if (this.oldObjects[index].size() == 0) {
                this.oldObjects[index] = null;
            }
        }
    }

    //Handling Concurrent Add
    private Comparable[] threadToId = new Comparable[16];

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
    /*if(versionNo>0){
            System.err.println("add "+object+" to"+index);
        }*/

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
            return;
            //throw new MerkleTreeException("Object not found");
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


                if (currentVersionNos[index] == this.versionNo) {
                    if (MerkleTreeLeafNode.isUpdated(this, index)) {
                        //if(versionNo>0)
                        //    System.err.println("Duplicate update for "+index);
                        return;
                    }
                    //System.out.println("No need to copy");
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                } else {
                    //System.out.println("Copy object");
                    int threadId = getThreadId();
                    this.setAllowAdding(threadId, false);
                    long createSeqNo = currentCreateVersionNos[index];
                    long seqNo = currentVersionNos[index];
                    if (oldObjects[index] == null) {
                        oldObjects[index] = new LinkedList<MerkleTreeLeafNode>();
                    }
		    /*if(object instanceof byte[]){
			System.err.println("Before copy "+index);
			Tools.printObject(object, this);
		    } */
                    oldObjects[index].addFirst(new MerkleTreeLeafNode(createSeqNo, seqNo, Tools.cloneObject(object), false, false));
		/*    if(object instanceof byte[]){
		//	System.err.println("Update for "+index);
		//	System.err.println("Before copy");
		//    	Tools.printObject(object, this);
			System.err.println("After copy");
			Tools.printObject(object, this);
		    }*/
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

                if (currentVersionNos[index] == this.versionNo) {
                    if (MerkleTreeLeafNode.isUpdated(this, index)) {
                        return;
                    }
                    //System.out.println("No need to copy");
                    MerkleTreeLeafNode.setUpdated(this, index, true);
                } else {
                    //System.out.println("Copy object");
                    long createSeqNo = currentCreateVersionNos[index];
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
        //System.out.println("Update " + object);
        //if(!(object instanceof MerkleTreeObject))
        //    System.out.println("Remove " + object.getClass());
        //System.out.println("Remove " + object);
        int index = getIndex(object);
        if (index == -1) {
            return;
            //throw new MerkleTreeException("Object not found");
        }
        //System.out.println("index=" + index);
        /*LinkedList<MerkleTreeLeafNode> tmp = this.objects[index];
        if (tmp.getFirst().getObject() != object) {
        throw new RuntimeException("Unmatching object");
        }
        if (tmp.getFirst().isDeleted()) {
        throw new RuntimeException("Deleting a deleted object " + object);
        }
        synchronized (tmp) {
        this.removeFromMapper(tmp.getFirst().getObject());
        tmp.addFirst(new MerkleTreeLeafNode(index, this.versionNo, this.versionNo, null, false, true));
        tmp.getFirst().setDeleted();
        }
        this.setEmpty(index);
        markAsUpdated(index);   */
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

    //public byte[] ret = new byte[32];
    public byte[] getHash() {
        long startTime = System.nanoTime();
        noOfLeafHashes = 0;
        this.finishThisVersion();
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


            for (int j = 0; j < numOfThreads; j++) {
                MerkleTreeThread.getAllThreads().get(j).startHashWork(hashWorks);
            }

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
        try {
            Checksum checksum = new Checksum(parameters);
            if (!fakeHash) {
                for (int i = 0; i < hashList.length; i++)
                    checksum.update(hashList[i]);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                this.serializeMetadata(oos, versionNo);
                oos.flush();
                checksum.update(bos.toByteArray());
                byte[] ret = checksum.getValue();
                return ret;
            } else {
                return new byte[parameters.digestLength];
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            noOfLeafHashes = 0;
            Debug.debug(Debug.MODULE_MERKLE, "Gethash %s time=%d\n", Thread.currentThread(), (int) (System.currentTimeMillis() - startTime));
        }

    }

    protected byte[] getHash(int index, MessageDigest digest) throws IOException, NoSuchAlgorithmException, IllegalAccessException {
        //int hashSize = 0;
        if (fakeHash) {
            hashList[index] = new byte[0];
            return hashList[index];
        }
        Checksum checksum = new Checksum(parameters);
        TreeSet<Integer> works = dirtyList[index];
        for (Integer i : works) {
            if (currentObjects[i] == null || MerkleTreeLeafNode.isDeleted(this, i)) {
                MerkleTreeLeafNode.setUpdated(this, i, false);
                checksum.update(new byte[0]);
            } else {
                MerkleTreeLeafNode.setUpdated(this, i, false);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                MerkleTreeLeafNode.write(oos, this, i);
                oos.flush();
                checksum.update(bos.toByteArray());
                noOfLeafHashes++;
                //hashSize += bos.toByteArray().length;
            }
        }
        hashList[index] = checksum.getValue();
        //System.out.println("hash for "+versionNo+" "+works.size()+" bytes="+hashSize);
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
        Checksum checksum = new Checksum(parameters);
        if (currentObjects[index] == null || MerkleTreeLeafNode.isDeleted(this, index)) {
            return new byte[0];
        } else {
            MerkleTreeLeafNode.setUpdated(this, index, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            MerkleTreeLeafNode.write(oos, this, index);
            oos.flush();

            checksum.update(bos.toByteArray());
        }
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

    private void rebuildTransientState() throws Exception {
        if (currentObjects[1032].getClass().getName().endsWith("Database")) {
            System.out.println("Find database " + currentObjects[1032].getClass() + " at " + 1032);
            Method m = currentObjects[1032].getClass().getDeclaredMethod("createScanIndices", new Class[0]);
            m.invoke(currentObjects[1032], new Object[0]);
        }
    }

    public void rollBack(long versionNo) throws MerkleTreeException {
        clearDirtyList(true);
        int threadId = getThreadId();
        this.setAllowAdding(threadId, false);

        Debug.info(Debug.MODULE_MERKLE, "rollBack to %d currentVersion=%d, stableVersion=%d\n", versionNo, this.versionNo, this.stableVersionNo);
        if (versionNo < this.stableVersionNo) {
            throw new MerkleTreeException("Cannot rollback before a stable version " + stableVersionNo);
        }
        this.versionNo = versionNo;
        for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
            if (currentObjects[i] == null) {
                continue;
            }
            if (currentVersionNos[i] <= versionNo)
                continue;
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
                    //System.out.println("replace "+createSeqNo+" "+tmp.getCreateVersionNo());
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
			    /*if(current instanceof byte[]){
			        System.err.println("Rollback object "+i);
			        Tools.printObject(oldObjects[i].getFirst().getObject(), this);
			    }*/
                        }

                    } else {
                        if (!oldObjects[i].getFirst().isCurrent()) {
                            oldObjects[i].getFirst().setStaticAsCurrent(this);
                        }
                    }
                    //System.out.println("Rollback "+i);
                    oldObjects[i].getFirst().setUpdated(true);
                    oldObjects[i].removeFirst().toCurrent(this, i);

                    if (oldObjects[i].size() == 0) {
                        oldObjects[i] = null;
                    }

                } else {
                    MerkleTreeLeafNode.setUpdated(this, i, true);
                    currentObjects[i] = null;
                    oldObjects[i] = null;
                    removeFromMapper(current);
                }
                if (currentObjects[i] != null && !MerkleTreeLeafNode.isDeleted(this, i)) {
                    this.setUsed(i);
                } else {

                    this.setEmpty(i);
                }
                MerkleTreeLeafNode.setUpdated(this, i, false);
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
        this.finishThisVersion();
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
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                this.clearEntry(i, true);
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
            //System.out.println("Find new "+tmp.getClass());
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
        Debug.info(Debug.MODULE_MERKLE, "fetchStates %d to %d\n", startVersionNo, targetVersionNo);
        this.finishThisVersion();
        try {
            //System.out.println("Got fetchState "+startVersionNo+" to "+targetVersionNo);
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
                    Iterator<MerkleTreeLeafNode> iter = oldObjects[i].descendingIterator();
                    while (iter.hasNext()) {
                        MerkleTreeLeafNode tmp = iter.next();
                        //if(this==MerkleTreeExecInstance.getShimInstance())
                        //    System.out.println(i+" "+tmp.getVersionNo());

                        if (tmp.getVersionNo() <= targetVersionNo && tmp.getVersionNo() > startVersionNo) {
                            //if(i==2) System.out.println("put ("+i+":"+tmp.getVersionNo()+") to state "+UnsignedTypes.bytesToHexString(tmp.getHash()));
                            oos.writeInt(i);
                            tmp.write(oos, this);
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
                //if(index==2) System.out.println("Merge States " + index+" "+tmp.isStatic());
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
                        //if(tmp.getVersionNo()<=this.objects[index].getFirst().getVersionNo())
                        //    continue;
                        Object current = currentObjects[index];
                        long createSeqNo = currentCreateVersionNos[index];
                        long seqNo = currentVersionNos[index];
                        //if(index==2) System.out.println("load "+index+" seqNo="+tmp.getVersionNo()+" createSeqNo="+tmp.getCreateVersionNo());
                        if (createSeqNo == tmp.getCreateVersionNo() && !MerkleTreeLeafNode.isDeleted(this, index)) {

                            oldObjects[index].addFirst(new MerkleTreeLeafNode(createSeqNo, seqNo, Tools.cloneObject(current), false, false));
                            if (tmp.getObject() != null) {
                                //A normal merge
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
                            oldObjects[index].addFirst(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
                        }
                    } else {
                        MerkleTreeLeafNode.setStaticAsOld(this, index);
                        oldObjects[index].add(MerkleTreeLeafNode.newMerkleTreeLeafNode(this, index));
                    }
                    tmp.toCurrent(this, index);
                    this.clearEntry(index, true);
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
                //System.out.println("load "+tmp.getIndex());
                toConnect.add(tmp);
                if (currentObjects[index] == null) {
                    tmp.toCurrent(this, index);
                    //currentObjects[index] = tmp;
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
        //this.getHash();
        try {
            StringBuilder builder = new StringBuilder();
            MessageDigest digest = MessageDigest.getInstance(parameters.digestType);
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
                if (oldObjects[i] != null) {
                    for (MerkleTreeLeafNode t : oldObjects[i]) {
                        builder.append("\t" + +t.getCreateVersionNo() + "->" + t.getVersionNo() + " " + t.isDeleted() + ":");
                        //builder.append("\t" + tmp.isDeleted() + "\t");
                        if (t.getObject() != null) {
                            builder.append(" " + t.getObject().getClass() + " " + t.getObject() + "\n");
                        } else {
                            builder.append("\n");
                        }
                    }
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
            MessageDigest digest = MessageDigest.getInstance(parameters.digestType);
            for (int i = 0; i < this.noOfObjects * this.mergeFactor; i++) {
                if (currentObjects[i] == null) {
                    continue;
                }
                System.err.print("object[" + i + "]:");
                if (currentObjects[i] != null) {
                    System.err.print(currentCreateVersionNos[i] + " " + currentVersionNos[i] + " " + currentFlags[i] + " ");
                    System.err.println(UnsignedTypes.bytesToHexString(getLeafHash1(i, digest)) + " " + currentObjects[i].getClass());
                    if (currentObjects[i].getClass().getName().endsWith("Row")) {
                        Tools.printObject(currentObjects[i], this);
                    }
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

    public byte[] pbFetchStates(long startVersionNo, long targetVersionNo, int[] indexes) throws MerkleTreeException {
        throw new MerkleTreeException("This should not be called in EVE");
    }

    public int[] pbRollBack(long versionNo) throws MerkleTreeException {
        throw new MerkleTreeException("This should not be called in EVE");
    }
}
