package ru.ifmo.nds.util;

import org.eclipse.collections.api.map.primitive.MutableObjectDoubleMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static <T> JFBYNonDominationLevel<T> removeIndividualFromLevel(@Nonnull final JFBYNonDominationLevel<T> lastLevel,
                                                                          @Nonnull final IIndividual<T> removedIndividual,
                                                                          @Nonnull final JFB2014 sorter) {
        final List<IIndividual<T>> newMembers = new ArrayList<>(lastLevel.getMembers().size());
        for (IIndividual<T> individual : lastLevel.getMembers()) {
            if (individual != removedIndividual) {
                newMembers.add(individual);
            }
        }

        final CrowdingDistanceData<T> cdd = Utils.recalcCrowdingDistances(
                removedIndividual.getObjectives().length,
                lastLevel.getSortedObjectives(),
                Collections.emptyList(),
                Collections.singleton(removedIndividual),
                newMembers);

        return new JFBYNonDominationLevel<>(sorter, cdd.getIndividuals(), cdd.getSortedObjectives());
    }

    public static <T> IIndividual<T> getWorstCDIndividual(@Nonnull final INonDominationLevel<T> lastLevel) {
        if (lastLevel.getMembers().size() < 3) {
            return lastLevel.getMembers().get(0);
        } else {
            final List<IIndividual<T>> lastLevelWithCD = lastLevel.getMembers();
            IIndividual<T> worstIndividual = null;
            for (IIndividual<T> individual : lastLevelWithCD) {
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

    /**
     * Inefficient full O(NlogN) CD recalc
     *
     * @param objCount Number of objectives
     * @param members  Level members
     * @return Members with calculated CD, sorted by each objective, etc.
     */
    public static <T> CrowdingDistanceData<T> calculateCrowdingDistances(final int objCount,
                                                                         @Nonnull final List<IIndividual<T>> members) {
        final int n = members.size();

        final List<IIndividual<T>> frontCopy = new ArrayList<>(n);
        frontCopy.addAll(members);

        final List<List<IIndividual<T>>> sortedObj = new ArrayList<>(objCount);

        final Map<IIndividual<T>, Double> cdMap = new IdentityHashMap<>();
        for (int i = 0; i < objCount; i++) {
            frontCopy.sort(new ObjectiveComparator(i));
            sortedObj.add(new ArrayList<>(frontCopy));

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

        final List<IIndividual<T>> rs = new ArrayList<>();
        for (IIndividual<T> member : members) {
            rs.add(new FitnessAndCdIndividual<>(member.getObjectives(), cdMap.get(member), member.getPayload()));
        }
        return new CrowdingDistanceData<>(rs, sortedObj);
    }

    public static <T> CrowdingDistanceData<T> recalcCrowdingDistances(final int objCount,
                                                                      @Nonnull final List<List<IIndividual<T>>> sortedObjectives,
                                                                      @Nonnull final List<IIndividual<T>> addends,
                                                                      @Nonnull final Set<IIndividual<T>> removed,
                                                                      @Nonnull final List<IIndividual<T>> targetStateLexSortedNoCd) {

        final int targetSize = sortedObjectives.get(0).size() - removed.size() + addends.size();

        final double[] mins = new double[objCount];
        final double[] maxs = new double[objCount];
        for (int obj = 0; obj < objCount; ++obj) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (IIndividual member : targetStateLexSortedNoCd) {
                min = Math.min(min, member.getObjectives()[obj]);
                max = Math.max(max, member.getObjectives()[obj]);
            }
            mins[obj] = min;
            maxs[obj] = max;
        }

        final MutableObjectDoubleMap<IIndividual<T>> cdMap = new ObjectDoubleHashMap<>(targetSize);
        final List<List<IIndividual<T>>> newSortedObjectives = new ArrayList<>(sortedObjectives.size());
        final List<IIndividual<T>> sortedAddends = new ArrayList<>(addends);
        for (int obj = 0; obj < objCount; ++obj) {
            final ArrayList<IIndividual<T>> newSortedObjective =
                    mergeSortedObjectiveWithAddendsAndRemovals(sortedObjectives, removed, targetSize, sortedAddends, obj);
            newSortedObjectives.add(newSortedObjective);
            updateCdMap(mins, maxs, cdMap, obj, newSortedObjective);
        }

        final List<IIndividual<T>> rs = new ArrayList<>(targetSize);
        for (IIndividual<T> i : targetStateLexSortedNoCd) {
            rs.add(new FitnessAndCdIndividual<>(i.getObjectives(), cdMap.get(i), i.getPayload()));
        }
        return new CrowdingDistanceData<>(rs, newSortedObjectives);
    }

    private static <T> ArrayList<IIndividual<T>> mergeSortedObjectiveWithAddendsAndRemovals(
            @Nonnull final List<List<IIndividual<T>>> sortedObjectives,
            @Nonnull final Set<IIndividual<T>> removed,
            final int targetSize,
            @Nonnull final List<IIndividual<T>> addends,
            final int objNumber) {
        final ArrayList<IIndividual<T>> newSortedObjective = new ArrayList<>(targetSize);
        final List<IIndividual<T>> oldSortedObjective = sortedObjectives.get(objNumber);
        int cAddends = 0;
        int cOldSorted = 0;
        final ObjectiveComparator comparator = new ObjectiveComparator(objNumber);
        addends.sort(comparator);
        while (newSortedObjective.size() < targetSize) {
            if (cOldSorted >= oldSortedObjective.size()) {
                newSortedObjective.add(addends.get(cAddends++));
            } else if (cAddends >= addends.size()) {
                final IIndividual<T> individual = oldSortedObjective.get(cOldSorted);
                if (!removed.contains(individual)) {
                    newSortedObjective.add(individual);
                }
                ++cOldSorted;
            } else if (comparator.compare(addends.get(cAddends), oldSortedObjective.get(cOldSorted)) <= 0) {
                newSortedObjective.add(addends.get(cAddends++));
            } else {
                final IIndividual<T> individual = oldSortedObjective.get(cOldSorted);
                if (!removed.contains(individual)) {
                    newSortedObjective.add(individual);
                }
                ++cOldSorted;
            }
        }
        return newSortedObjective;
    }

    private static <T> void updateCdMap(@Nonnull final double[] mins,
                                        @Nonnull final double[] maxs,
                                        @Nonnull final MutableObjectDoubleMap<IIndividual<T>> cdMap,
                                        final int objNumber,
                                        @Nonnull final ArrayList<IIndividual<T>> newSortedObjective) {
        cdMap.put(newSortedObjective.get(0), Double.POSITIVE_INFINITY);
        cdMap.put(newSortedObjective.get(newSortedObjective.size() - 1), Double.POSITIVE_INFINITY);
        final double inverseDelta = 1 / (maxs[objNumber] - mins[objNumber]);
        for (int j = 1; j < newSortedObjective.size() - 1; j++) {
            double distance = cdMap.getIfAbsentPut(newSortedObjective.get(j), 0.0);
            distance += (newSortedObjective.get(j + 1).getObjectives()[objNumber] -
                    newSortedObjective.get(j - 1).getObjectives()[objNumber]) * inverseDelta;
            cdMap.put(newSortedObjective.get(j), distance);
        }
    }
}
