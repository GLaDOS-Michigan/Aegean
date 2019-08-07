// $Id: Parameters.java 730 2011-09-12 00:43:48Z aclement $


package BFT.order;

public class Parameters extends BFT.Parameters {

    // frequency with which order checkpoints are taken
    public static int checkPointInterval = 100;
    // number of checkpoint intervals that we maintain
    public static int maxPeriods = 2;

    // base duration of a view in number of checkpoint intervals
    public static int baseDuration = 100;
    // heartbeat interval, in milliseconds
    public static long heartBeat = 4000;

    // allow time to be +/- 10 sec of local time
    public static long timeVariance = 1000000;

    public static int maxBatchSize = 100000;

}
