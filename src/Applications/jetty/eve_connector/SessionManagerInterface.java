package Applications.jetty.eve_connector;

import javax.servlet.http.HttpSession;

public interface SessionManagerInterface {
    HttpSession getSession(String sessionID, boolean createOnNull);
}
