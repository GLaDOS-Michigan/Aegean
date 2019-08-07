package BFT.exec.glue;

import BFT.exec.RequestHandler;

public class UnreplicatedGlueThread implements Runnable {
    transient RequestHandler handler;
    transient private GeneralGlueTuple tuple = null;

    public UnreplicatedGlueThread(RequestHandler handler, GeneralGlueTuple tuple) {
        this.handler = handler;
        this.tuple = tuple;
    }

    @Override
    public void run() {
        handler.execRequest(tuple.request, tuple.info);
    }
}