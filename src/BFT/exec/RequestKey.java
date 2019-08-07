/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BFT.exec;

/**
 * @author yangwang
 */
public class RequestKey {

    private boolean read;
    private Object key;

    public RequestKey(boolean read, Object key) {
        this.read = read;
        this.key = key;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isWrite() {
        return !read;
    }

    public Object getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object target) {
        RequestKey tmp = (RequestKey) target;
        return tmp.read == this.read && tmp.key.equals(this.key);
    }
}
