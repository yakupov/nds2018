package ru.ifmo.nds.util;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Utils {
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

    public static JFBYNonDominationLevel removeIndividualFromLevel(@Nonnull final INonDominationLevel lastLevel,
                                                                   final IIndividual removedIndividual,
                                                                   @Nonnull final JFB2014 sorter) {
        final List<IIndividual> newMembers = new ArrayList<>(lastLevel.getMembers().size());
        for (IIndividual individual : lastLevel.getMembers()) {
            if (individual != removedIndividual) {
                newMembers.add(individual);
            }
        }
        return new JFBYNonDominationLevel(sorter, newMembers);
    }

    public static IIndividual getWorstCDIndividual(@Nonnull final INonDominationLevel lastLevel) {
        final int numberOfObjectives = lastLevel.getMembers().get(0).getObjectives().length;
        if (lastLevel.getMembers().size() < 3) {
            return lastLevel.getMembers().get(0);
        } else {
            return getWorstCDIndividualBigLevel(lastLevel, numberOfObjectives);
        }
    }

    private static IIndividual getWorstCDIndividualBigLevel(@Nonnull final INonDominationLevel lastLevel,
                                                            final int numberOfObjectives) {
        final int n = lastLevel.getMembers().size();
        final List<IIndividual> frontCopy = new ArrayList<>(lastLevel.getMembers().size());
        frontCopy.addAll(lastLevel.getMembers());
        final Map<IIndividual, Double> cdMap = new IdentityHashMap<>();
        for (int i = 0; i < numberOfObjectives; i++) {
            frontCopy.sort(new ObjectiveComparator(i));
            cdMap.put(frontCopy.get(0), Double.POSITIVE_INFINITY);
            cdMap.put(frontCopy.get(n - 1), Double.POSITIVE_INFINITY);

            final double minObjective = frontCopy.get(0).getObjectives()[i];
            final double maxObjective = frontCopy.get(n - 1).getObjectives()[i];
            for (int j = 1; j < n - 1; j++) {
                double distance = cdMap.getOrDefault(frontCopy.get(j), 0.0);
                distance += (frontCopy.get(j + 1).getObjectives()[i] -
                        frontCopy.get(j - 1).getObjectives()[i])
                        / (maxObjective - minObjective);
                cdMap.put(frontCopy.get(j), distance);
            }
        }
        IIndividual worstIndividual = null;
        for (IIndividual individual : frontCopy) {
            if (worstIndividual == null || cdMap.get(worstIndividual) > cdMap.get(individual)) {
                worstIndividual = individual;
            }
        }
        return worstIndividual;
    }
}
