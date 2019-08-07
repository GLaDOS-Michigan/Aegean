/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.exec;

import java.util.List;

/**
 * @author yangwang
 */
public interface RequestFilter {
    public List<RequestKey> generateKeys(byte[] request);
}
