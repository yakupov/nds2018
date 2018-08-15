package ru.ifmo.nds.util;

import ru.ifmo.nds.IIndividual;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Comparator;

@ThreadSafe
public class AscLexSortComparator implements Comparator<IIndividual> {
    private static AscLexSortComparator instance = new AscLexSortComparator();

    private AscLexSortComparator() {
    }

    public static AscLexSortComparator getInstance() {
        return instance;
    }

    @Override
    public int compare(IIndividual o1, IIndividual o2) {
        final double[] o1Obj = o1.getObjectives();
        final double[] o2Obj = o2.getObjectives();
        for (int i = 0; i < o1Obj.length; ++i) {
            if (o1Obj[i] < o2Obj[i])
                return -1;
            else if (o1Obj[i] > o2Obj[i])
                return 1;
        }
        return 0;
    }
}
