/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.exec;

/**
 * @author yangwang
 */
public interface ReplyHandler {
    public void result(byte[] reply, RequestInfo info);

    public void readOnlyResult(byte[] reply, RequestInfo info);
}
