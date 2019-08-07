// $Id: TreeNode.java 57 2010-02-27 22:19:03Z yangwang $

package BFT.order.statemanagement.viewchangetree;

import BFT.messages.Digest;
import BFT.messages.HistoryDigest;

import java.util.Iterator;
import java.util.Vector;

public class TreeNode {
    protected Digest dig;
    protected HistoryDigest hist;
    protected long seqno;
    protected boolean[] support;
    protected int supportCount;

    protected Vector<TreeNode> children;
    protected int orderCount;

    public TreeNode(Digest d, HistoryDigest h, long seqno, int orderCount) {
        this.seqno = seqno;
        this.orderCount = orderCount;
        dig = d;
        hist = h;
        supportCount = 0;
        support = new boolean[orderCount];
        for (int i = 0; i < support.length; i++) {
            support[i] = false;
        }

        children = new Vector<TreeNode>();
    }

    public Digest getDigest() {
        return dig;
    }

    public HistoryDigest getHistory() {
        return hist;
    }

    public void addSupport(int replica) {
        if (!support[replica]) {
            support[replica] = true;
            supportCount++;
        }
    }

    public TreeNode getChild(Digest d, HistoryDigest h) {
        Iterator<TreeNode> it = children.iterator();
        boolean found = false;
        TreeNode tmp = null;
        while (it.hasNext() && !found) {
            tmp = it.next();
            if (tmp.getHistory().equals(h) && tmp.getDigest().equals(d)) {
                found = true;
            }
        }
        if (!found) {
            tmp = new TreeNode(d, h, seqno + 1, orderCount);
            children.add(tmp);
        }
        return tmp;
    }

    public Iterator<TreeNode> getChildren() {
        return children.iterator();
    }

    public int getSupportCount() {
        return supportCount;
    }

    public boolean[] getSupport() {
        return support;
    }

    public long getSeqNo() {
        return seqno;
    }

    public long getSequenceNumber() {
        return seqno;
    }

    public String print(String p) {
        String str = p + "+Node(" + seqno + ", " + supportCount + " [";
        for (int i = 0; i < support.length; i++) {
            str = str + (support[i] ? " " + i : " -");
        }

        str = str + "])\n";
        Iterator<TreeNode> it = children.iterator();
        int count = 100;
        while (it.hasNext()) {
            TreeNode node = it.next();
            str = str + node.print(p + " ");
            count++;
        }

        return str;
    }
}
