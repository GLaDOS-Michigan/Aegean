package util;

public class Counter {

    int count;

    public Counter() {
        count = 0;
    }

    public int increment() {
        return ++count;
    }

    public int decrement() {
        return --count;
    }

    public boolean isZero() {
        return count == 0;
    }

    public String toString() {
        return "" + count;
    }
}