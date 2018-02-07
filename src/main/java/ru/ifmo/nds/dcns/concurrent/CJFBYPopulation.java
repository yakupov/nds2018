package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.impl.CDIndividualWithRank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ru.ifmo.nds.util.Utils.*;

@ThreadSafe
public class CJFBYPopulation extends AbstractConcurrentJFBYPopulation {
    private class LevelRef {
        final long modificationTs;
        final INonDominationLevel level;

        LevelRef(long modificationTs, INonDominationLevel level) {
            this.modificationTs = modificationTs;
            this.level = level;
        }
    }

    private final CopyOnWriteArrayList<AtomicReference<LevelRef>> nonDominationLevels;
    private final AtomicLong time = new AtomicLong(0); //TODO: process overflow

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
        this.nonDominationLevels = new CopyOnWriteArrayList<>();
        this.nonDominationLevels.addAll(nonDominationLevels.stream()
                .map(ref -> new AtomicReference<>(new LevelRef(0, ref.get())))
                .collect(Collectors.toList()));

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
            @SuppressWarnings("unchecked") final AtomicReference<LevelRef> levelRefRef =
                    (AtomicReference<LevelRef>) levels[j];
            final LevelRef levelRef = levelRefRef.get();
            if (levelRef != null) {
                final INonDominationLevel level = levelRef.level;
                levelsSnapshot.set(j, level);
                sizeSnapshot += level.getMembers().size();
            }
        }
        final List<INonDominationLevel> rs = new ArrayList<>();
        for (INonDominationLevel level : levelsSnapshot) {
            if (level != null) {
                rs.add(level);
            }
        }
        return new PopulationSnapshot(rs, sizeSnapshot);
    }

    @Nullable
    @Override
    public IIndividual removeWorst() {
        throw new UnsupportedOperationException("Explicit deletion of members not allowed");
    }

    @Nullable
    @Override
    IIndividual intRemoveWorst() {
        while (true) {
            try {
                final int lastLevelIndex = nonDominationLevels.size() - 1;
                final LevelRef lastLevelRef = nonDominationLevels.get(lastLevelIndex).get();
                if (lastLevelRef == null) {
                    continue;
                }
                final INonDominationLevel lastLevel = lastLevelRef.level;
                if (lastLevel.getMembers().size() <= 1) {
                    try {
                        addRemoveLevelLock.lock();
                        if (lastLevelIndex == nonDominationLevels.size() - 1 &&
                                nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevelRef, null)) {
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
                    final IIndividual removedIndividual = getWorstCDIndividual(lastLevel);
                    final JFBYNonDominationLevel newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);

                    if (nonDominationLevels.get(lastLevelIndex)
                            .compareAndSet(lastLevelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
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
    public List<CDIndividualWithRank> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }

        final PopulationSnapshot populationSnapshot = getLevelsSnapshot();
        final int actualCount = Math.min(count, populationSnapshot.getSize());
        final int[] indices = ThreadLocalRandom.current()
                .ints(actualCount * 3, 0, populationSnapshot.getSize())
                .distinct().sorted().limit(actualCount).toArray();
        final List<CDIndividualWithRank> res = new ArrayList<>();
        int i = 0;
        int prevLevelsSizeSum = 0;
        int rank = 0;
        for (INonDominationLevel level : populationSnapshot.getLevels()) {
            final int levelSize = level.getMembers().size();
            while (i < actualCount && indices[i] - prevLevelsSizeSum < levelSize) {
                final CDIndividual cdIndividual = level.getMembersWithCD().get(indices[i] - prevLevelsSizeSum);
                res.add(new CDIndividualWithRank(cdIndividual.getIndividual(), cdIndividual.getCrowdingDistance(), rank));
                ++i;
            }
            prevLevelsSizeSum += levelSize;
            ++rank;
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
            try {
                final LevelRef levelRef = nonDominationLevels.get(test).get();
                if (levelRef == null) {
                    r--;
                } else if (!levelRef.level.dominatedByAnyPointOfThisLayer(point)) {
                    //Racy, but that's OK
                    lastNonDominating = test;
                    r = test - 1;
                } else {
                    l = test + 1;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                r--;
            }
        }

        return lastNonDominating;
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        final long insertionTs = time.incrementAndGet();
        int rank = determineMinimalPossibleRank(addend);
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        }

        final Integer firstModifiedLevelRank;
        if (useOneByOneSorting) {
            firstModifiedLevelRank = insertOneByOne(addend, rank, insertionTs);
        } else {
            firstModifiedLevelRank = insertViaFullSortings(addend, rank);
        }

        if (size.incrementAndGet() > expectedPopulationSize) {
            intRemoveWorst();
        }

        return Objects.requireNonNull(firstModifiedLevelRank, "Impossible situation: the point was not added");
    }

    private Integer insertOneByOne(@Nonnull final IIndividual originalAddend, int rank, final long ts) {
        Integer firstModifiedLevelRank = null;
        List<IIndividual> addends = new LinkedList<>();
        addends.add(originalAddend);
        while (!addends.isEmpty()) {
            try {
                if (rank >= nonDominationLevels.size()) {
                    if (tryToAddLevels(addends, rank)) {
                        if (firstModifiedLevelRank == null) {
                            return rank;
                        } else {
                            return firstModifiedLevelRank;
                        }
                    }
                } else {
                    final LevelRef levelRef = nonDominationLevels.get(rank).get();
                    if (levelRef == null) {
                        continue;
                    }
                    final INonDominationLevel level = levelRef.level;
                    if (ts > levelRef.modificationTs) {
                        final INonDominationLevel.MemberAdditionResult mar = level.addMembers(addends);
                        if (nonDominationLevels.get(rank).compareAndSet(levelRef, new LevelRef(time.incrementAndGet(), mar.getModifiedLevel()))) {
                            if (firstModifiedLevelRank == null) {
                                firstModifiedLevelRank = rank;
                            }
                            ++rank;
                            addends = mar.getEvictedMembers();
                        }
                    } else {
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
                        if (nonDominationLevels.get(rank).compareAndSet(levelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                            if (firstModifiedLevelRank == null) {
                                firstModifiedLevelRank = rank;
                            }
                            addends = nextAddends;
                            ++rank;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }

        return firstModifiedLevelRank;
    }

    private boolean tryToAddLevels(List<IIndividual> addends, int minPossibleRank) {
        addRemoveLevelLock.lock();
        try {
            if (minPossibleRank >= nonDominationLevels.size()) {
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
                    nonDominationLevels.add(new AtomicReference<>(
                            new LevelRef(time.incrementAndGet(), new JFBYNonDominationLevel(sorter, level))));
                }
                return true;
            }
        } finally {
            addRemoveLevelLock.unlock();
        }
        return false;
    }

    private Integer insertViaFullSortings(@Nonnull IIndividual addend, int rank) {
        Integer firstModifiedLevelRank = null;
        List<IIndividual> addends = new ArrayList<>();
        addends.add(addend);
        while (!addends.isEmpty()) {
            try {
                if (rank >= nonDominationLevels.size()) {
                    if (tryToAddLevels(addends, rank)) {
                        if (firstModifiedLevelRank == null) {
                            return rank;
                        } else {
                            return firstModifiedLevelRank;
                        }
                    }
                } else {
                    final LevelRef levelRef = nonDominationLevels.get(rank).get();
                    if (levelRef == null) {
                        continue;
                    }
                    final INonDominationLevel level = levelRef.level;
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
                    if (nonDominationLevels.get(rank).compareAndSet(levelRef, new LevelRef(time.incrementAndGet(), newLevel))) {
                        if (firstModifiedLevelRank == null) {
                            firstModifiedLevelRank = rank;
                        }
                        addends = nextAddends;
                        ++rank;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
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
        for (AtomicReference<LevelRef> levelRef : nonDominationLevels) {
            list.add(new AtomicReference<>(levelRef.get().level.copy()));
        }
        return new CJFBYPopulation(list, expectedPopulationSize, useOneByOneSorting);
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CJFBYPopulation{");
        sb.append("nonDominationLevels=").append(nonDominationLevels);
        sb.append(", time=").append(time);
        sb.append(", presentIndividuals=").append(presentIndividuals);
        sb.append(", addRemoveLevelLock=").append(addRemoveLevelLock);
        sb.append(", size=").append(size);
        sb.append(", sorter=").append(sorter);
        sb.append(", expectedPopulationSize=").append(expectedPopulationSize);
        sb.append(", useOneByOneSorting=").append(useOneByOneSorting);
        sb.append('}');
        return sb.toString();
    }
}
