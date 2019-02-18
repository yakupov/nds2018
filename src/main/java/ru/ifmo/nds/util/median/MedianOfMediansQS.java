package ru.ifmo.nds.util.median;

import javax.annotation.concurrent.ThreadSafe;

/**
 * QS using Median of Medians pivot selection strategy
 */
@SuppressWarnings("unused")
@ThreadSafe
public class MedianOfMediansQS extends QuickSelect {
    @Override
    int calcPivot(final double[] values, final int from, final int to) {
        if (to - from < 5) {
            return partition5(values, from, to);
        } else {
            for (int i = from; i <= to; i += 5) {
                final int median5 = partition5(values, i, Math.min(i + 4, to));
                swap(values, median5, (int) (from + Math.floor((i - from) / 5)));
            }
        }
        return select(values, from, (int) (from + Math.ceil((to - from) / 5) - 1), from + (to - from) / 10);
    }

    private int partition5(double[] values, int from, int to) {
        if (values == null || from >= values.length || to >= values.length) {
            throw new IllegalArgumentException("Null array or incorrect indices");
        } else {
            double t;
            for (int i = from + 1; i <= to; i++) {
                t = values[i];
                int j = i - 1;
                while (j >= 0 && values[j] > t) {
                    values[j + 1] = values[j];
                    --j;
                }
                values[j + 1] = t;
            }

            return (to - from) / 2 + from;
        }
    }
}
