/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev;

/**
 * @author manos
 */
public class HierarchicalBitmap {

    public final int base = 32;
    protected int[] bitmap;
    protected int internalNodes;
    protected int size;
    protected int actualSize;
    protected int[] masks;
    protected int[] clearMasks;
    protected int levels;
    protected final int fullMask = ~0;
    int numOfObjects;

    public HierarchicalBitmap(int noOfObjects) {
        numOfObjects = noOfObjects;
        if (noOfObjects <= 32) {
            noOfObjects = 33;
        }
        for (levels = 0; actualSize < noOfObjects; levels++) {
            size += (int) Math.pow(base, levels);
            if (levels > 0) {
                internalNodes += (int) Math.pow(base, levels - 1);
            }
            actualSize = (size - internalNodes) * base;
        }
        //System.out.println("levels = "+levels);
        bitmap = new int[size];

        generateMasks();
        //System.out.println("size = "+size+" internal = "+internalNodes+" actualSize = "+actualSize);
    }

    public static void main(String args[]) {
        //HierarchicalBitmap hb4 = new HierarchicalBitmap(1);
        HierarchicalBitmap hb = new HierarchicalBitmap(Integer.parseInt(args[0]));
        //hb.bitmap[hb.size-1] = 9;
        //hb.printBitmap();

        //hb.set(31);
        //System.out.println(hb.get(4));
        //System.out.println(hb.get(5));
        //System.out.println(hb.get(1));

        
        /*hb.set(0);
        hb.set(1);
        hb.set(2);
        hb.set(31);*/
        //hb.set(1022);
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                hb.set(i * 32 + j);
                //for(int k=0;k<32;k++) {
                //    hb.set(i*32*32+j*32+k);
                //}
            }
        }

        //hb.clear(24);
        //hb.clear();
        /*for(int i=0;i<32;i++) {
            hb.set(i);
        }*/

        //hb.set(22345);
        //hb.clearBit(22345);
        //hb.getFirstClearBit(0);
        //hb.getFirstClearBit(1);
        //hb.getFirstClearBit(8);
        //hb.printBitmap();
        /*hb.clear(1023);
        a = hb.nextClearBit();
        System.out.println("a = "+a);
        hb.set(1023);
        a = hb.nextClearBit();
        System.out.println("a = "+a);
        */
        /*hb.clearBit(0);
        hb.clearBit(1);
        hb.clearBit(2);
        hb.clearBit(32);*/

        /*hb.setBit(33);
        hb.setBit(53);
        hb.setBit(67);
        hb.clearBit(33);
        hb.clearBit(53);*/
        /*hb.setBit(1);
        hb.setBit(32);
        hb.setBit(33);
        hb.clearBit(32);
        hb.setBit(1023);*/
        //hb.setBit(1024);

        //hb.printBitmap();
        //HierarchicalBitmap hb6 = new HierarchicalBitmap(3);
    }

    public boolean get(int index) {
        if (index >= actualSize) {
            throw new RuntimeException("get: invalid index " + index + " where actualSize = " + actualSize +
                    " levels=" + levels + "objects=" + numOfObjects);
        }
        int whichInt = index / base + internalNodes;
        int whichBit = index % base;

        int data = bitmap[whichInt];
        if ((data & masks[whichBit]) == 0) {
            return false;
        } else {
            return true;
        }
    }

    public void clear(int index) {
        if (index >= actualSize) {
            throw new RuntimeException("clear: invalid index " + index + " where actualSize = " + actualSize + " levels=" + levels);
        }
        int whichInt = index / base + internalNodes;
        int whichBit = index % base;
        //System.out.println("index ="+index+" whichInt = "+whichInt+" mask = "+masks[whichBit]);
        //printInt(bitmap[whichInt]);
        bitmap[whichInt] &= clearMasks[whichBit];

        //int parentIndex = (whichInt-1)/base;
        //int parentBit = (whichInt-1)%base;
        //System.out.println("out");
        //printInt(bitmap[whichInt]);
        //while(bitmap[whichInt] == 0) {
        while (true) {
            whichBit = (whichInt - 1) % base;
            whichInt = (whichInt - 1) / base;
            //System.out.println("parentIndex = "+whichInt+" parentBit = "+whichBit);
            //int temp = bitmap[parentIndex]
            bitmap[whichInt] &= clearMasks[whichBit];
            if (whichInt == 0) {
                break;
            }
            //parentBit = (parentIndex)%base;
            //parentIndex = (parentIndex)/base;

        }
        //System.out.println("new value = "+bitmap[whichInt]);
        //printInt(bitmap[whichInt]);
    }

    public void set(int index) {
        if (index >= actualSize) {
            throw new RuntimeException("set: invalid index " + index + " where actualSize = " + actualSize + " levels=" + levels);
        }
        int whichInt = index / base + internalNodes;
        int whichBit = index % base;
        //System.out.println("index ="+index+" whichInt = "+whichInt+" mask = "+masks[whichBit]);
        //printInt(bitmap[whichInt]);
        bitmap[whichInt] |= masks[whichBit];

        //System.out.println("initial parentIndex = "+parentIndex+" initial parentBit = "+parentBit);
        while (bitmap[whichInt] == fullMask) {
            whichBit = (whichInt - 1) % base;
            whichInt = (whichInt - 1) / base;
            //System.out.println("parentIndex = "+whichInt+" parentBit = "+whichBit);
            //int temp = bitmap[parentIndex]
            bitmap[whichInt] |= masks[whichBit];
            if (whichInt == 0) {
                break;
            }
        }
        //System.out.println("new value = "+bitmap[whichInt]);
        //printInt(bitmap[whichInt]);
    }

    public int nextClearBit(int index) {
        return nextClearBit();
    }

    public int nextClearBit() {
        if (bitmap[0] == fullMask) {
            return actualSize;
        }
        int index = 0;
        int nextIndex = 0;
        for (int level = 0; level < levels; level++) {
            //System.out.println("index = "+index);
            index = base * (index) + getFirstClearBit(index) + 1;
        }

        index -= base * internalNodes + 1;
        //System.out.println("First clear bit is in index "+index);

        return index;
    }

    public int cardinality() {
        return actualSize;
    }

    public int getFirstClearBit(int index) {
        int data = bitmap[index];
        for (int i = 0; i < base; i++) {
            if ((data & masks[i]) == 0) {
                //System.out.println("getFirstClearBit["+index+"]="+(i));
                return i;
            }
        }
        //System.out.println("getFirstClearBit["+index+"]="+(base-1));
        return base - 1;
    }

    public void printInternal() {
        System.out.println("Printing internal nodes");
        for (int i = 0; i < internalNodes; i++) {
            int data = bitmap[i];
            printInt(data);
        }
    }

    public void printLeafNodes() {
        System.out.println("Printing leaf nodes");
        for (int i = internalNodes; i < size; i++) {
            int data = bitmap[i];
            printInt(data);
        }
    }

    public void printBitmap() {
        printInternal();
        printLeafNodes();
    }

    public void printInt(int data) {
        int mask = 0x80000000;
        for (int j = 0; j < base; j++) {
            //System.out.println(mask);
            if ((data & mask) != 0) {
                System.out.print(1);
            } else {
                System.out.print(0);
            }
            mask = mask >> 1;
            mask = mask & 0x7FFFFFFF;
            //printBit(data,j);
        }
        System.out.println();
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            bitmap[i] = 0;
        }
    }

    private void generateMasks() {
        masks = new int[base];
        clearMasks = new int[base];
        int mask = 1;
        for (int i = 0; i < base; i++) {
            masks[i] = mask;
            clearMasks[i] = ~mask;
            //System.out.println("masks["+i+"] = "+masks[i]);
            mask = mask << 1;
        }

    }
}
