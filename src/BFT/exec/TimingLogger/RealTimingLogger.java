package BFT.exec.TimingLogger;

import BFT.Debug;

import java.util.Arrays;

/**
 * Created by jimgiven on 7/29/15.
 */
public class RealTimingLogger implements TimingLogger {

    private transient String name;
    private transient int freq;
    private transient long[] times;
    private transient int counter;
    private transient int window;
    private transient double factor;
    private transient long lastStart;

    public RealTimingLogger(String name, int freq) {
        this.name = name;
        this.freq = freq;
        this.times = new long[freq];
        //this.window = freq / 100;
        this.window = 0;
        this.factor = 1000000;
        this.counter = 0;
        this.lastStart = -1;

        assert 2 * window < freq;
    }

    public boolean hasStarted() {
        return lastStart != -1;
    }

    public void logStart() {
        //Debug.warning(Debug.MODULE_EXEC, this.name + " Log Start Called: %d\n", System.nanoTime());
        //assert lastStart == -1;
        lastStart = System.nanoTime();
    }

    public void logEnd() {
        assert counter < freq;
        long time = System.nanoTime() - lastStart;
        times[counter] = time;
        counter++;
        if (counter == freq) {
            printTimes();
        }
        lastStart = -1;
    }

    public void printTimes() {
        double sum = 0;
        if (window != 0) {
            Arrays.sort(times);
        }
        for (int i = window; i < (counter - window); ++i) {
            sum += times[i];
        }
        double avg = (sum / (counter - window));
        String str = "TimingLogger:" + name + ":" + (avg / factor);
        Debug.warning(Debug.MODULE_EXEC, str);
        if (window != 0) {
            double low = 0, high = 0;
            for (int i = 0; i < window; ++i) {
                low += times[i];
                high += times[counter - i - 1];
            }
            String str2 = "TimingLoggerOutside:" + name + ":" + (low / (window * factor)) + "," + (high / (window * factor));
            Debug.warning(Debug.MODULE_EXEC, str2);
            //showStandardDeviation(avg);
        }
        counter = 0;
    }

    public void showStandardDeviation(double avg) {
        double sum = 0;
        for (int i = window; i < (counter - window); ++i) {
            sum += Math.pow(times[i] - avg, 2);
        }
        double std = Math.sqrt(sum / (counter - window));
        Debug.warning(Debug.MODULE_EXEC, "TimingLogger:std:" + std);
    }
}
