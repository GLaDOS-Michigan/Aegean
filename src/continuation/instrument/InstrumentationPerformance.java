package continuation.instrument;

import org.apache.commons.javaflow.Continuation;

import java.util.Random;

public class InstrumentationPerformance {
    private int[] values;
    private int size;

    public void sort(int[] values) {
        // check for empty or null array
        if (values == null || values.length == 0) {
            return;
        }
        this.values = values;
        size = values.length;
        quicksort(0, size - 1);
    }

    private void quicksort(int low, int high) {
        int i = low, j = high;
        int pivot = values[low + (high - low) / 2];

        while (i <= j) {
            while (values[i] < pivot) {
                i++;
            }
            while (values[j] > pivot) {
                j--;
            }

            if (i <= j) {
                exchange(i, j);
                i++;
                j--;
            }
        }
        // Recursion
        if (low < j)
            quicksort(low, j);
        if (i < high)
            quicksort(i, high);
    }

    private int decreate(int j) {
        return j - 1;
    }

    private int increase(int i) {
        return i + 1;
    }

    private boolean lessThanOrEqualTo(int a, int b) {
        return a <= b;
    }

    private void exchange(int i, int j) {
        int temp = values[i];
        values[i] = values[j];
        values[j] = temp;
    }

    public static void main(String args[]) {
        Random rand = new Random();
        final int[] values = new int[1024 * 1024 * 32];
        for (int i = 0; i < values.length; i++) {
            values[i] = rand.nextInt();
        }
        long start = System.currentTimeMillis();

        Continuation.startWith(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                new InstrumentationPerformance().sort(values);
            }
        });

        long end = System.currentTimeMillis();
        System.out.println("Time: " + (end - start));
    }

}
