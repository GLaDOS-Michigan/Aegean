/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.exec;

/**
 * @author yangwang
 */
public interface RequestHandler {
    public void execRequest(byte[] request, RequestInfo info);

    public void execReadOnly(byte[] request, RequestInfo info);

}
