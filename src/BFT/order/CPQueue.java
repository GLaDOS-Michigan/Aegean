package BFT.order;

import BFT.order.statemanagement.CheckPointState;

public interface CPQueue {

    public abstract void addWork(CheckPointState cp);

    public abstract CheckPointState getCPWork();

}