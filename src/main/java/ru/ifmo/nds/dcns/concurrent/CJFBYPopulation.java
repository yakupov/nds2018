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

import static ru.ifmo.nds.util.Utils.lexCompare;

@ThreadSafe
public class CJFBYPopulation implements IManagedPopulation {
    private final CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels;
    private final Lock addRemoveLevelLock = new ReentrantLock();
    private final AtomicInteger size = new AtomicInteger(0); //Cannot actually be decreased

    private final JFB2014 sorter = new JFB2014();
    private final int expectedPopulationSize; //Members will not be deleted if the size is less or equal to this value

    public CJFBYPopulation(int expectedPopulationSize) {
        this(new CopyOnWriteArrayList<>(), expectedPopulationSize);
    }

    private CJFBYPopulation(CopyOnWriteArrayList<AtomicReference<INonDominationLevel>> nonDominationLevels,
                            int expectedPopulationSize) {
        this.nonDominationLevels = nonDominationLevels;
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

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }
        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
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
        return new CJFBYPopulation(list, expectedPopulationSize);
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
