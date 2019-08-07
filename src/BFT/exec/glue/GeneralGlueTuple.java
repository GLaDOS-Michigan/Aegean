/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.exec.glue;

import BFT.exec.RequestInfo;

import java.util.Arrays;

/**
 * @author yangwang
 */
public class GeneralGlueTuple {
    public final byte[] request;
    public final RequestInfo info;

    public GeneralGlueTuple(byte[] request, RequestInfo info) {
        this.request = request;
        this.info = info;
    }

    public GeneralGlueTuple(GeneralGlueTuple tuple) {
        this.request = Arrays.copyOf(tuple.request, tuple.request.length);
        this.info = new RequestInfo(tuple.info);
    }
}
