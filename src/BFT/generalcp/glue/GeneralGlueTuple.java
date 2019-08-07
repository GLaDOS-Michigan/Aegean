/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.generalcp.glue;

import BFT.generalcp.RequestInfo;


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
}