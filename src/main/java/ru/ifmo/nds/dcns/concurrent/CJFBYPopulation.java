package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.util.ObjectiveComparator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ru.ifmo.nds.util.Utils.lexCompare;

@ThreadSafe
public class CJFBYPopulation implements IManagedPopulation {
    private final CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels;
    private final Map<IIndividual, Boolean> presentIndividuals = new ConcurrentHashMap<>();
    private final Lock addRemoveLevelLock = new ReentrantLock();
    private final AtomicInteger size = new AtomicInteger(0); //Cannot actually be decreased

    private final JFB2014 sorter = new JFB2014();
    private final int expectedPopulationSize; //Members will not be deleted if the size is less or equal to this value
    private final boolean useOneByOneSorting;

    @SuppressWarnings("WeakerAccess")
    public CJFBYPopulation(int expectedPopulationSize, boolean useOneByOneSorting) {
        this(new CopyOnWriteArrayList<>(), expectedPopulationSize, useOneByOneSorting);
    }

    @SuppressWarnings("WeakerAccess")
    public CJFBYPopulation(CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels,
                           int expectedPopulationSize, boolean useOneByOneSorting) {
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopulationSize = expectedPopulationSize;
        this.useOneByOneSorting = useOneByOneSorting;

        if (!nonDominationLevels.isEmpty()) {
            int ctr = 0;
            for (AtomicReference<INonDominationLevel> levelRef : nonDominationLevels) {
                for (IIndividual individual : levelRef.get().getMembers()) {
                    presentIndividuals.put(individual, true);
                    ++ctr;
                }
            }
            size.set(ctr);
        }
    }

    @Override
    @Nonnull
    public List<INonDominationLevel> getLevels() {
        return getLevelsSnapshot().getLevels();
    }

    private PopulationSnapshot getLevelsSnapshot() {
        int sizeSnapshot = 0;
        final Object[] levels = nonDominationLevels.toArray();
        final List<INonDominationLevel> levelsSnapshot = Arrays.asList(new INonDominationLevel[levels.length]);
        for (int j = levels.length - 1; j >= 0; --j) { //Reverse iterator because elements may only move to latter levels, and we do not want any collisions
            @SuppressWarnings("unchecked") final AtomicReference<INonDominationLevel> levelRef =
                    (AtomicReference<INonDominationLevel>) levels[j];
            final INonDominationLevel level = levelRef.get();
            levelsSnapshot.set(j, level);
            sizeSnapshot += level.getMembers().size();
        }
        return new PopulationSnapshot(levelsSnapshot, sizeSnapshot);
    }

    @Nullable
    @Override
    public IIndividual removeWorst() {
        throw new UnsupportedOperationException("Explicit deletion of members not allowed");
    }

    @Nullable
    IIndividual intRemoveWorst() {
        while (true) {
            try {
                final int lastLevelIndex = nonDominationLevels.size() - 1;
                final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex).get();
                if (lastLevel.getMembers().size() <= 1) {
                    try {
                        addRemoveLevelLock.lock();
                        if (lastLevelIndex == nonDominationLevels.size() - 1 &&
                                nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevel, null)) {
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                return null;
                            } else {
                                final IIndividual rs = lastLevel.getMembers().get(0);
                                presentIndividuals.remove(rs);
                                return rs;
                            }
                        }
                    } finally {
                        addRemoveLevelLock.unlock();
                    }
                } else {
                    final int n = lastLevel.getMembers().size();
                    final int numberOfObjectives = lastLevel.getMembers().get(0).getObjectives().length;
                    final IIndividual removedIndividual;
                    if (n < 3) {
                        removedIndividual = lastLevel.getMembers().get(0);
                    } else {
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
                        removedIndividual = worstIndividual;
                    }
                    final List<IIndividual> newMembers = new ArrayList<>(lastLevel.getMembers().size());
                    for (IIndividual individual : lastLevel.getMembers()) {
                        if (individual != removedIndividual) {
                            newMembers.add(individual);
                        }
                    }
                    if (nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevel, new JFBYNonDominationLevel(sorter, newMembers))) {
                        presentIndividuals.remove(removedIndividual);
                        return removedIndividual;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                //retry
            }
        }
    }

    @Nonnull
    @Override
    public List<IIndividual> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }

        final PopulationSnapshot populationSnapshot = getLevelsSnapshot();
        final int actualCount = Math.min(count, populationSnapshot.getSize());
        final int[] indices = ThreadLocalRandom.current()
                .ints(0, this.size.get(), actualCount * 3)
                .distinct().sorted().limit(actualCount).toArray();
        final List<IIndividual> res = new ArrayList<>();
        int i = 0;
        int prevLevelsSizeSum = 0;
        for (INonDominationLevel level : populationSnapshot.getLevels()) {
            final int levelSize = level.getMembers().size();
            while (indices[i] - prevLevelsSizeSum < levelSize) {
                res.add(level.getMembers().get(indices[i] - prevLevelsSizeSum));
                ++i;
            }
            prevLevelsSizeSum += levelSize;
        }

        return res;
    }

    @Override
    public int size() {
        return size.get();
    }

    private int determineMinimalPossibleRank(IIndividual point) {
        int l = 0;
        int r = nonDominationLevels.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            if (!nonDominationLevels.get(test).get().dominatedByAnyPointOfThisLayer(point)) {
                //Racy, but that's OK
                lastNonDominating = test;
                r = test - 1;
            } else {
                l = test + 1;
            }
        }

        return lastNonDominating;
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        int rank = determineMinimalPossibleRank(addend);
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        }

        final Integer firstModifiedLevelRank;
        if (useOneByOneSorting) {
            firstModifiedLevelRank = insertOneByOne(addend, rank);
        } else {
             firstModifiedLevelRank = insertViaFullSortings(addend, rank);
        }

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }

        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
    }

    private Integer insertOneByOne(@Nonnull IIndividual addend, int rank) {
        Integer firstModifiedLevelRank = null;
        Queue<IIndividual> addends = new LinkedList<>();
        addends.add(addend);
        while (!addends.isEmpty()) {
            if (rank >= nonDominationLevels.size()) {
                try {
                    addRemoveLevelLock.lock();
                    rank = determineMinimalPossibleRank(addend);
                    if (rank >= nonDominationLevels.size()) {
                        final List<IIndividual> individuals = new ArrayList<>();
                        individuals.addAll(addends);
                        final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, individuals);
                        nonDominationLevels.add(new AtomicReference<>(level));
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        break;
                    }
                } finally {
                    addRemoveLevelLock.unlock();
                }
            } else {
                final Queue<IIndividual> nextAddends = new LinkedList<>();
                while (!addends.isEmpty() && rank < nonDominationLevels.size()) {
                    final INonDominationLevel level = nonDominationLevels.get(rank).get();
                    final IIndividual nextAddend = addends.peek();
                    if (!level.dominatedByAnyPointOfThisLayer(nextAddend)) {
                        final INonDominationLevel.MemberAdditionResult mar = level.addMembers(Collections.singletonList(nextAddend));
                        if (nonDominationLevels.get(rank).compareAndSet(level, mar.getModifiedLevel())) {
                            if (firstModifiedLevelRank == null) {
                                firstModifiedLevelRank = rank;
                            }
                            nextAddends.addAll(mar.getEvictedMembers());
                            addends.poll();
                        }
                    } else {
                        nextAddends.add(addends.poll());
                    }
                }
                addends = nextAddends;
                ++rank;
            }
        }

        return firstModifiedLevelRank;
    }


        private Integer insertViaFullSortings(@Nonnull IIndividual addend, int rank) {
        Integer firstModifiedLevelRank = null;
        List<IIndividual> addends = new ArrayList<>();
        addends.add(addend);
        while (!addends.isEmpty()) {
            if (rank >= nonDominationLevels.size()) {
                try {
                    addRemoveLevelLock.lock();
                    if (rank >= nonDominationLevels.size()) {
                        final IIndividual[] members = addends.toArray(new IIndividual[addends.size()]);
                        final int[] ranks = sorter.performNds(members);
                        final List<List<IIndividual>> levels = new ArrayList<>();
                        for (int i = 0; i < members.length; ++i) {
                            while (levels.size() <= ranks[i]) {
                                levels.add(new ArrayList<>());
                            }
                            levels.get(ranks[i]).add(members[i]);
                        }
                        for (List<IIndividual> level : levels) {
                            nonDominationLevels.add(new AtomicReference<>(new JFBYNonDominationLevel(sorter, level)));
                        }
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        break;
                    }
                } finally {
                    addRemoveLevelLock.unlock();
                }
            } else {
                final INonDominationLevel level = nonDominationLevels.get(rank).get();
                final IIndividual[] allMembers = lexMerge(addends, level.getMembers());
                final int[] ranks = sorter.performNds(allMembers);
                final List<IIndividual> newCurrLevelMembers = new ArrayList<>();
                final List<IIndividual> nextAddends = new ArrayList<>();
                for (int i = 0; i < allMembers.length; ++i) {
                    if (ranks[i] == 0) {
                        newCurrLevelMembers.add(allMembers[i]);
                    } else {
                        nextAddends.add(allMembers[i]);
                    }
                }
                final INonDominationLevel newLevel = new JFBYNonDominationLevel(sorter, newCurrLevelMembers);
                if (nonDominationLevels.get(rank).compareAndSet(level, newLevel)) {
                    if (firstModifiedLevelRank == null) {
                        firstModifiedLevelRank = rank;
                    }
                    addends = nextAddends;
                    ++rank;
                }
            }
        }
        return firstModifiedLevelRank;
    }

    private IIndividual[] lexMerge(@Nonnull final List<IIndividual> aList,
                                   @Nonnull final List<IIndividual> mList) {
        int ai = 0;
        int mi = 0;
        final IIndividual[] result = new IIndividual[aList.size() + mList.size()];
        while (mi < mList.size() || ai < aList.size()) {
            if (mi >= mList.size()) {
                result[ai + mi] = aList.get(ai);
                ++ai;
            } else if (ai >= aList.size()) {
                result[ai + mi] = mList.get(mi);
                ++mi;
            } else {
                final IIndividual m = mList.get(mi);
                final IIndividual a = aList.get(ai);
                if (lexCompare(m.getObjectives(), a.getObjectives(), a.getObjectives().length) <= 0) {
                    result[ai + mi] = m;
                    ++mi;
                } else {
                    result[ai + mi] = a;
                    ++ai;
                }
            }
        }
        return result;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     * Thread-safe because of the COW snapshot iterator
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public CJFBYPopulation clone() {
        final CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> list = new CopyOnWriteArrayList<>();
        for (AtomicReference<INonDominationLevel> level : nonDominationLevels) {
            list.add(new AtomicReference<>(level.get().copy()));
        }
        return new CJFBYPopulation(list, expectedPopulationSize, useOneByOneSorting);
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CJFBYPopulation{");
        sb.append("nonDominationLevels=").append(nonDominationLevels);
        sb.append(", addRemoveLevelLock=").append(addRemoveLevelLock);
        sb.append(", size=").append(size);
        sb.append(", expectedPopulationSize=").append(expectedPopulationSize);
        sb.append('}');
        return sb.toString();
    }
}
