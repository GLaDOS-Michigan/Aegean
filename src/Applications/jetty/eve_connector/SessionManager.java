package Applications.jetty.eve_connector;

import merkle.MerkleTreeInstance;
import merkle.wrapper.MTMapWrapper;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager implements SessionManagerInterface {

    private Map<String, HttpSession> sessionMap;

    @SuppressWarnings("unchecked")
    public SessionManager(boolean useMerkleTree) {
        if(useMerkleTree) {
            this.sessionMap = new MTMapWrapper(
                    new ConcurrentHashMap<String, HttpSession>(),
                    false,
                    true,
                    false
            );

            MerkleTreeInstance.addRoot(sessionMap);
        }
        else
        {
            this.sessionMap = new HashMap<>();
        }
    }

    @Override
    public HttpSession getSession(String sessionID, boolean createOnNull) {
        HttpSession res = this.sessionMap.get(sessionID);

        if (res == null && createOnNull) {
            res = new FakedSession();
            this.sessionMap.put(sessionID, res);
        }

        return res;
    }

}
