package BFT.exec.TimingLogger;

/**
 * Created by jimgiven on 7/29/15.
 */
public class FakeTimingLogger implements TimingLogger {
    public boolean hasStarted() {
        return false;
    }

    public void logStart() {
        return;
    }

    public void logEnd() {
        return;
    }

    public void printTimes() {
        return;
    }
}
