package Applications.jetty.eve_connector;

import merkle.MerkleTreeInstance;
import merkle.wrapper.MTMapWrapper;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class FakedSession implements HttpSession {

    private boolean isNewFlag = true;
    private Map<String, Object> valueMap = null;

    @SuppressWarnings("unchecked")
    public FakedSession() {
        this.isNewFlag = true;
        this.valueMap = new MTMapWrapper(
                new ConcurrentHashMap<String, Object>(),
                false,
                true,
                true
        );
    }

    @Override
    public Object getAttribute(String key) {
        return getValue(key);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public long getCreationTime() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public String getId() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public long getLastAccessedTime() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public int getMaxInactiveInterval() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public ServletContext getServletContext() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public HttpSessionContext getSessionContext() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public Object getValue(String key) {
        if (this.valueMap.containsKey(key))
            return this.valueMap.get(key);
        else return null;
    }

    @Override
    public String[] getValueNames() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public void invalidate() {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public boolean isNew() {
        if (this.isNewFlag) {
            MerkleTreeInstance.update(this);
            this.isNewFlag = false;
            return true;
        }

        return false;
    }

    @Override
    public void putValue(String key, Object value) {
        this.valueMap.put(key, value);
    }

    @Override
    public void removeAttribute(String arg0) {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public void removeValue(String arg0) {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public void setAttribute(String arg0, Object arg1) {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public void setMaxInactiveInterval(int arg0) {
        throw new RuntimeException("Method not implemented.");
    }

}
