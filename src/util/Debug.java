// $Id: Debug.java 710 2011-06-29 13:35:51Z aclement $
package util;

public class Debug {

    public static boolean debug = false;
    public static boolean profile = false;


    public static void log(boolean module, boolean level, String format, Object... args) {
        if (module && level) {
            System.out.printf(format, args);
        }
    }

    public static void println(Object obj) {
        if (debug) {
            System.out.println(obj);
        }
    }

    public static void println(String str) {
        if (debug) {
            System.out.println(str);
        }
    }

    public static void println(boolean cond, Object st) {
        if (debug && cond) {
            System.out.println(st);
        }
    }

    public static void println() {
        if (debug) {
            //System.out.println();
        }
    }

    public static void print(Object obj) {
        if (debug) {
            System.out.print(obj);
        }
    }

    static public void kill(Exception e) {
        e.printStackTrace();
        System.exit(0);
    }

    public static void kill(String st) {
        kill(new RuntimeException(st));
    }

    static protected long baseline = 0;//System.currentTimeMillis() - 1000000;

    public static void profileStart(String s) {
        if (!profile) {
            return;
        }
        String tmp = Thread.currentThread() + " " + s + " START " + (System.currentTimeMillis() - baseline);
        System.out.println(tmp);
    }

    public static void profileFinis(String s) {
        if (!profile) {
            return;
        }
        String tmp = Thread.currentThread() + " " + s + " FINIS " + (System.currentTimeMillis() - baseline);
        System.out.println(tmp);
    }

}
