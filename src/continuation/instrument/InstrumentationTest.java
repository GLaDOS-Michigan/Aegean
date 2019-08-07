package continuation.instrument;

import org.apache.commons.javaflow.Continuation;

public class InstrumentationTest {
    public static void main(String args[]) {
        try {
            Continuation c = Continuation.startWith(new TestRunnable());
            while (c != null) {
                System.out.println("Suspended.");
                c = Continuation.continueWith(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
