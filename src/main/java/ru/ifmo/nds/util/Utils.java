package ru.ifmo.nds.util;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;

import javax.annotation.Nonnull;
import java.util.Collections;

public class Utils {
    /**
     * Check the domination relation over the first K objectives.
     *
     * @param d1  First individual
     * @param d2  Second individual
     * @param dim Number of comparable coordinates in each individual (not max. index!)
     *            In the most common max. compared index will be {@code dim} - 1
     * @return -1 if {@code d1} dominates over {@code d2}. 1 if {@code d2} dominates over {@code d1}. 0 otherwise.
     */
    public static int dominates(double[] d1, double[] d2, int dim) {
        return dominatesByFirstCoordinatesV2(d1, d2, dim);
    }

    /**
     * @param d1  First individual
     * @param d2  Second individual
     * @param dim Number of comparable coordinates in each individual (not max. index!)
     * @return -1 if {@code d1} dominates over {@code d2}. 1 if {@code d2} dominates over {@code d1}. 0 otherwise.
     */
    private static int dominatesByFirstCoordinatesV2(double[] d1, double[] d2, int dim) {
        boolean d1less = false;
        boolean d2less = false;
        for (int currCoord = 0; currCoord < dim; ++currCoord) {
            if (d1[currCoord] < d2[currCoord]) {
                d1less = true;
            } else if (d1[currCoord] > d2[currCoord]) {
                d2less = true;
            }

            if (d1less && d2less) {
                return 0;
            }
        }

        if (d1less)
            return -1;
        else if (d2less)
            return 1;
        else
            return 0;
    }

    /**
     * Perform lexicographical comparison
     *
     * @param d1  First individual
     * @param d2  Second individual
     * @param dim Number of comparable coordinates in each individual (not max. index!)
     * @return -1 if {@code d1} is lexicographically smaller than {@code d2}. 1 if larger. 0 if equal.
     */
    public static int lexCompare(double[] d1, double[] d2, int dim) {
        assert (d1.length >= dim && d2.length >= dim);

        for (int i = 0; i < dim; ++i) {
            if (d1[i] < d2[i])
                return -1;
            else if (d1[i] > d2[i])
                return 1;
        }
        return 0;
    }

    public static <T> JFBYNonDominationLevel<T> removeIndividualFromLevel(@Nonnull final JFBYNonDominationLevel<T> lastLevel,
                                                                          @Nonnull final IIndividual<T> removedIndividual,
                                                                          @Nonnull final JFB2014 sorter) {
        final SortedObjectives<IIndividual<T>, T> nso = lastLevel.getSortedObjectives().update(
                Collections.emptyList(),
                Collections.singletonList(removedIndividual),
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );

        return new JFBYNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso);
    }

    public static <T> IIndividual<T> getWorstCDIndividual(@Nonnull final INonDominationLevel<T> lastLevel) {
        if (lastLevel.getMembers().size() < 3) {
            return lastLevel.getMembers().get(0);
        } else {
            IIndividual<T> worstIndividual = null;
            for (IIndividual<T> individual : lastLevel.getMembers()) {
                if (worstIndividual == null || worstIndividual.getCrowdingDistance() > individual.getCrowdingDistance()) {
                    worstIndividual = individual;
                }
            }
            if (worstIndividual != null) {
                return worstIndividual;
            } else {
                return null;
            }
        }
    }
}
