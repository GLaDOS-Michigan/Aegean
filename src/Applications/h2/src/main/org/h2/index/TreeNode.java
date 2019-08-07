/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import merkle.MerkleTreeInstance;
import org.h2.result.Row;

/**
 * Represents a index node of a tree index.
 */
public class TreeNode extends merkle.MerkleTreeObjectImp {

    /**
     * The balance. For more information, see the AVL tree documentation.
     */
    int balance;

    /**
     * The left child node or null.
     */
    public TreeNode left;

    /**
     * The right child node or null.
     */
    public TreeNode right;

    /**
     * The parent node or null if this is the root node.
     */
    public TreeNode parent;

    /**
     * The row.
     */
    private Row row;

    public TreeNode() {

    }

    public void copyObject(Object dst) {
        TreeNode target = (TreeNode) dst;
        target.balance = this.balance;
        target.left = this.left;
        target.right = this.right;
        target.parent = this.parent;
        target.row = this.row;
    }

    public Object cloneObject() {
        TreeNode target = new TreeNode();
        this.copyObject(target);
        return target;
    }

    TreeNode(Row row) {
        MerkleTreeInstance.add(this);
        if (!row.inTree) {
            MerkleTreeInstance.add(row);
            row.inTree = true;
        }
        this.setRow(row);
    }

    /**
     * Check if this node is the left child of its parent. This method returns
     * true if this is the root node.
     *
     * @return true if this node is the root or a left child
     */
    boolean isFromLeft() {
        return parent == null || parent.left == this;
    }

    public void setRow(Row row) {
        if (!row.inTree) {
            MerkleTreeInstance.add(row);
            row.inTree = true;
        }
        /*if (this.row != null)
		{
			this.row.referenceCounter--;
		}
		row.referenceCounter++;*/
        MerkleTreeInstance.update(this);
        this.row = row;

    }

    public Row getRow() {
        return row;
    }

}
