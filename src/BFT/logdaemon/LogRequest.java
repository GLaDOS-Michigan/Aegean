package BFT.logdaemon;

public class LogRequest {
    public static final int WRITE = 0;
    public static final int READ = 1;
    public static final int BARRIER = 2;
    public static final int GC = 3;

    public static final int WRITE_OK = 0;
    public static final int WRITE_FAIL = 1;
    public static final int READ_OK = 2;
    public static final int READ_FAIL = 3;
    public static final int BARRIER_OK = 4;
    public static final int BARRIER_FAIL = 5;
    public static final int GC_OK = 6;
    public static final int GC_FAIL = 7;

}