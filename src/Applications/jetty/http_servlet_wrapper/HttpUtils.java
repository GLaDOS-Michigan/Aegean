package Applications.jetty.http_servlet_wrapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lcosvse
 */

public class HttpUtils {
    public static String[] keys = {
            "%20", "%22", "%23", "%24", "%27", "%28", "%29", "%2A", "%2B", "%2C",
            "%2D", "%2E", "%2F", "%3A", "%3B", "%3C", "%3E", "%40", "%5B", "%5C",
            "%5D", "%5E", "%60", "%7B", "%7D", "%7E", "%7C", "%25", "%21", "%3F", "%3D", "%5F", "%26"
    };

    public static String[] values = {" ", "\"", "#", "$", "'", "(", ")", "*", "+", ",",
            "-", ".", "/", ":", ";", "<", ">", "@", "[", "\\", "]", "^",
            "`", "{", "}", "~", "|", "%", "!", "?", "=", "_", "&"
    };

    public static Map<String, String> replace = null;
    public static Map<String, String> encode = null;

    public static void initMap() {
        replace = new ConcurrentHashMap<String, String>();
        encode = new ConcurrentHashMap<String, String>();

        assert (keys.length == values.length);

        int i;
        for (i = 0; i < keys.length; i++) {
            replace.put(keys[i], values[i]);
        }
        for (i = 0; i < keys.length; i++) {
            if (values[i].equals("?"))
                break;
            encode.put(values[i], keys[i]);
        }
    }
}
