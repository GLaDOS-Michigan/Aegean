package JDT;

import merkle.MerkleTree;

public class SimpleTest {

    int myInt;
    String myString;
    byte[] myByteArray;
    byte[][][] myByteTripleArray;
    Inner a;
    static Inner b;

    public SimpleTest() {
        super();
        MerkleTree.update(this);
        this.myInt = 3;
        MerkleTree.update(this);
        a = new Inner();
        MerkleTree.update(this);
        this.myInt = 0;
        MerkleTree.update(this);
        myString = "my first string ever";
        MerkleTree.update(this);
        myByteArray = new byte[13];
        MerkleTree.update(this);
        myByteTripleArray = new byte[4][][];
        for (int i = 0; i < 4; i++) {
            myByteTripleArray[i] = new byte[5][];
            for (int j = 0; j < 5; j++) {
                myByteTripleArray[i][j] = new byte[6];
            }
        }
    }

    public int getMyInt() {
        int f = 0;
        boolean flag = true;
        MerkleTree.update(this);
        if (f == (myInt = 1)) {
            flag = true;
        }
        if (true) {
            MerkleTree.update(this);
            a = null;
        } else {
            MerkleTree.update(this);
            myInt = 4;
        }

        for (int i = 0; i < 4; i++) {
            MerkleTree.update(this);
            myInt = i;
        }

        for (int i = 0; i < 4; i++) {
            MerkleTree.update(this);
            myInt = 2 * i;
        }

        f = (flag == true ? 1 : 2);
        int i;
        for (i = 0; i < 10; i++) {
            i++;
        }
        MerkleTree.update(this);
        a = new Inner();
        MerkleTree.update(a);
        a.x = 3;
        MerkleTree.update(this.a);
        this.a.x = 4;
        int testlocal;
        testlocal = 0;
        myByteArray[2] = 1;
        myByteArray[1] = 0;
        myByteTripleArray[1][2][3] = 4;
        SimpleTest ss = new SimpleTest();
        MerkleTree.updateStatic(JDT.SimpleTest.Inner);
        ss.b = new Inner();

        switch (i) {
            case 1:
                MerkleTree.update(this);
                myInt = 1;
            case 2:
                MerkleTree.update(this);
                myInt = 2;
        }

        SystemWrapper.arraycopy(myByteArray, 0, myByteTripleArray[0][0], 0, 1);

        return this.myInt;
    }

    class Inner {
        public int x;
    }
}
