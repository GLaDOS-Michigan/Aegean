/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package BFT.exec.messages;

/**
 * @author yangwang
 */
public class MessageTags extends BFT.messages.MessageTags {
    public static final int ExecuteBatch = 50;
    public static final int FetchState = 51;
    public static final int AppState = 52;
    public static final int PBRollback = 53;
    public static final int PBFetchState = 54;
}
