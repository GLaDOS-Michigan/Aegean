package Applications.tpcw_webserver.rbe;

import java.io.PrintStream;
import java.net.URL;

/**
 * Interface for EBs to issue requests through
 * client-shim
 *
 * @author lcosvse
 */
public interface EBClientProxyInterface {
    byte[] getHTMLText(String req);

    int getImgs(URL rd);

    void setOutStream(PrintStream outStream);

    int getCompletedRequestCount();
}
