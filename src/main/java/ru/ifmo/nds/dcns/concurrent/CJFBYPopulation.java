package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class CJFBYPopulation implements IManagedPopulation {
    private final CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels;
    private final Lock addRemoveLevelLock = new ReentrantLock();
    private final AtomicInteger size = new AtomicInteger(0); //Cannot actually be decreased

    private final JFB2014 sorter;
    private final int expectedPopulationSize; //Members will not be deleted if the size is less or equal to this value


    public CJFBYPopulation(JFB2014 sorter, int expectedPopulationSize) {
        this(new CopyOnWriteArrayList<>(), sorter, expectedPopulationSize);
    }

    private CJFBYPopulation(CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels,
                            JFB2014 sorter,
                            int expectedPopulationSize) {
        this.nonDominationLevels = nonDominationLevels;
        this.sorter = sorter;
        this.expectedPopulationSize = expectedPopulationSize;
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
    private IIndividual intRemoveWorst() {
        throw new UnsupportedOperationException("Not needed to test NDS"); //TODO: impl later, considering CD
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
    public int addIndividual(IIndividual addend) {
        int rank = determineMinimalPossibleRank(addend);
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

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }
        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
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
        return new CJFBYPopulation(list, sorter, expectedPopulationSize);
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
