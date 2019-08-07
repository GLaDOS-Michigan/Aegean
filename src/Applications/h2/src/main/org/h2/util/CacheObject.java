/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import merkle.MerkleTreeInstance;
import org.h2.constant.SysProperties;
import org.h2.message.Message;
import org.h2.store.DiskFile;

import java.util.Comparator;

/**
 * The base object for all cached objects.
 */
public abstract class CacheObject {

    /**
     * Compare cache objects by position.
     */
    static class CacheComparator implements Comparator<CacheObject> {
        public int compare(CacheObject a, CacheObject b) {
            return MathUtils.compare(a.getPos(), b.getPos());
        }
    }

    /**
     * Ensure the class is loaded when initialized, so that sorting is possible
     * even when loading new classes is not allowed any more. This can occur
     * when stopping a web application.
     */
    static {
        new CacheComparator();
    }

    /**
     * The previous element in the LRU linked list. If the previous element is
     * the head, then this element is the most recently used object.
     */
    public CacheObject cachePrevious;

    /**
     * The next element in the LRU linked list. If the next element is the head,
     * then this element is the least recently used object.
     */
    public CacheObject cacheNext;

    /**
     * The next element in the hash chain.
     */
    public CacheObject cacheChained;

    /**
     * The cache queue identifier. This field is only used for the 2Q cache
     * algorithm.
     */
    public int cacheQueue;

    /**
     * The number of blocks occupied by this object.
     */
    protected int blockCount;

    private int pos;
    private boolean changed;

    /**
     * Check if the object can be removed from the cache.
     * For example pinned objects can not be removed.
     *
     * @return true if it can be removed
     */
    public abstract boolean canRemove();

    /**
     * Order the given list of cache objects by position.
     *
     * @param recordList the list of cache objects
     */
    public static void sort(ObjectArray<CacheObject> recordList) {
        recordList.sort(new CacheComparator());
    }

    public void setBlockCount(int size) {
        MerkleTreeInstance.update(this);
        this.blockCount = size;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setPos(int pos) {
        if (SysProperties.CHECK && (cachePrevious != null || cacheNext != null || cacheChained != null)) {
            Message.throwInternalError("setPos too late");
        }
        MerkleTreeInstance.update(this);
        this.pos = pos;
    }

    public int getPos() {
        return pos;
    }

    /**
     * Check if this cache object has been changed and thus needs to be written
     * back to the storage.
     *
     * @return if it has been changed
     */
    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean b) {
        MerkleTreeInstance.update(this);
        changed = b;
    }

    public void copyObject(Object dst) {
        CacheObject target = (CacheObject) dst;
        target.cachePrevious = this.cachePrevious;

        target.cacheNext = this.cacheNext;

        target.cacheChained = this.cacheChained;

        target.cacheQueue = this.cacheQueue;

        target.blockCount = this.blockCount;

        target.pos = this.pos;
        target.changed = this.changed;
    }

    /**
     * Check if this cache object can be removed from the cache.
     *
     * @return if it can be removed
     */
    public boolean isPinned() {
        return false;
    }

    /**
     * Get the estimated memory size.
     *
     * @return number of double words (4 bytes)
     */
    public int getMemorySize() {
        return blockCount * (DiskFile.BLOCK_SIZE / 4);
    }

}
