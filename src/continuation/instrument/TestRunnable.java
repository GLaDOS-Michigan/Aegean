package continuation.instrument;

import org.apache.commons.javaflow.Continuation;

public class TestRunnable implements Runnable {
    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println(i);
            Continuation.suspend();
        }
    }
}
