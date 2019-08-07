package BFT.exec;

import BFT.Parameters;

public class FakeFailureDetector {

    private final int index;
    private int primary;
    private int dead;


    public FakeFailureDetector(int index, Parameters param) {
        this.index = index;
        primary = 0;
        dead = -1;

    }

    public int primary() {
        return primary;
    }

    public int dead() {
        return dead;
    }
}
    
