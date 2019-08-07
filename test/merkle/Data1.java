package merkle;
import java.io.Serializable;

public class Data1 implements Serializable {
    private int value1;
    private long value2;
    public static short staticValue;

    public transient byte nonSerializable;

    public Data1() {
        MerkleTreeInstance.add(this);
    }


    public Data1(int value1, long value2) {
        MerkleTreeInstance.add(this);
        this.value1 = value1;
        this.value2 = value2;
    }

    public void setValue1(int value){
        System.out.println("Hehre");
        MerkleTreeInstance.update(this);
        this.value1 = value;
    }

    public String toString() {
        return "(" + value1 + " " + value2 + ")";
    }
}
