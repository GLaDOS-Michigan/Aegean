package BFT.order;

public interface NBLogQueue {

    public abstract void addWork(int num, NBLogWrapper nb);

    public abstract NBLogWrapper getNBWork(int num, boolean block);

}