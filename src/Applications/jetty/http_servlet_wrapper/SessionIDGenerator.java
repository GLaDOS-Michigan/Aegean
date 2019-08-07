package Applications.jetty.http_servlet_wrapper;

import BFT.exec.Info;
import BFT.exec.RequestInfo;

import java.util.Random;

/**
 * @author lcosvse
 */

public class SessionIDGenerator {
    static String generateSessionId(Info requestInfo) {

        String encodeKey = "" + requestInfo.getClientId() + " " + requestInfo.getSubId();

        int hashCode = encodeKey.hashCode();

        Random rand = new Random();
        rand.setSeed(hashCode);

        String tempSessionId = "";

        for (int i = 0; i < 32; i++) {
            char ch = (char) ((char) rand.nextInt(26) + 'a');
            tempSessionId = tempSessionId + ch;
        }

        return tempSessionId;
    }
}
