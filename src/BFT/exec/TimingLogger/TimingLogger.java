package BFT.exec.TimingLogger;

/**
 * Created by jimgiven on 7/29/15.
 */
public interface TimingLogger {

    public boolean hasStarted();

    public void logStart();

    public void logEnd();

    public void printTimes();
}
