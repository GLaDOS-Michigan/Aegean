// $Id: Debug.java 674 2011-03-08 16:56:19Z yangwang $
package BFT;

public class Debug {

    public static boolean debug = false;
    public static boolean profile = false;

    public static boolean COMMIT = false;
    public static boolean VIEWCHANGE = true;
    public static boolean STATUS = false;
    public static boolean QUEUE = false;
    public static boolean WAITS = false;

    public static boolean MODULE_MERKLE = true;
    public static boolean MODULE_EXEC = true;
    public static boolean MODULE_VERIFIER = true;
    public static boolean MODULE_FILTER = true;
    public static boolean MODULE_CLIENT = true;
    public static boolean MODULE_NETWORK = true;
    public static boolean MODULE_MESSAGE = true;
    public static boolean MODULE_BASENODE = true;

    private static int printCounter = 0;

    public static void log(boolean module, boolean level, String format, Object... args) {
        if (module && level) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void fine(boolean module, String format, Object... args) {
        if (module && BFT.Parameters.level_fine) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void debug(boolean module, String format, Object... args) {
        if (module && BFT.Parameters.level_debug) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void info(boolean module, String format, Object... args) {
        if (module && BFT.Parameters.level_info) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void warning(boolean module, String format, Object... args) {
        if (module && BFT.Parameters.level_warning) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void error(boolean module, String format, Object... args) {
        if (module && BFT.Parameters.level_error) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }

    public static void trace(boolean module, boolean trace, String log) {
        if(module && trace) {
            System.out.println("trace: " + log);
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

    public static void printQueue(String s) {
        if (QUEUE) {
            System.out.println(s);
        }
    }

    public static void printWaits(String s) {
        if (WAITS) {
            System.out.println(s);
        }
    }

    public static void printfSparse(int modulo, String format, Object... args) {
        printCounter = (printCounter + 1) % 10000;
        if (printCounter % modulo == 0) {
            System.out.printf(format.endsWith("\n") ? format : (format + "\n"), args);
        }
    }
}
