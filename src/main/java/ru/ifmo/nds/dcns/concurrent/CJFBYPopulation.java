package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.util.CrowdingDistanceData;
import ru.ifmo.nds.util.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ru.ifmo.nds.util.Utils.getWorstCDIndividual;
import static ru.ifmo.nds.util.Utils.lexCompare;
import static ru.ifmo.nds.util.Utils.removeIndividualFromLevel;

@ThreadSafe
public class CJFBYPopulation<T> implements IManagedPopulation<T> {
    private class LevelRef {
        final long modificationTs;
        final JFBYNonDominationLevel<T> level;

        LevelRef(long modificationTs, JFBYNonDominationLevel<T> level) {
            this.modificationTs = modificationTs;
            this.level = level;
        }
    }

    private final CopyOnWriteArrayList<AtomicReference<LevelRef>> nonDominationLevels;
    private final AtomicLong time = new AtomicLong(0); //todo: process overflow

    private final Map<IIndividual<T>, Boolean> presentIndividuals = new ConcurrentHashMap<>();
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
    public CJFBYPopulation(CopyOnWriteArrayList<AtomicReference<JFBYNonDominationLevel<T>>> nonDominationLevels,
                           int expectedPopulationSize,
                           boolean useOneByOneSorting) {
        this.nonDominationLevels = new CopyOnWriteArrayList<>();
        this.nonDominationLevels.addAll(nonDominationLevels.stream()
                .map(ref -> new AtomicReference<>(new LevelRef(0, ref.get())))
                .collect(Collectors.toList()));

        this.expectedPopulationSize = expectedPopulationSize;
        this.useOneByOneSorting = useOneByOneSorting;

        if (!nonDominationLevels.isEmpty()) {
            int ctr = 0;
            for (AtomicReference<JFBYNonDominationLevel<T>> levelRef : nonDominationLevels) {
                for (IIndividual<T> individual : levelRef.get().getMembers()) {
                    presentIndividuals.put(individual, true);
                    ++ctr;
                }
            }
            size.set(ctr);
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        int sizeSnapshot = 0;
        final Object[] levels = nonDominationLevels.toArray();
        @SuppressWarnings("unchecked") final List<INonDominationLevel<T>> levelsSnapshot = Arrays.asList(new INonDominationLevel[levels.length]);
        for (int j = levels.length - 1; j >= 0; --j) { //Reverse iterator because elements may only move to latter levels, and we do not want any collisions
            @SuppressWarnings("unchecked") final AtomicReference<LevelRef> levelRefRef =
                    (AtomicReference<LevelRef>) levels[j];
            final LevelRef levelRef = levelRefRef.get();
            if (levelRef != null) {
                final INonDominationLevel<T> level = levelRef.level;
                levelsSnapshot.set(j, level);
                sizeSnapshot += level.getMembers().size();
            }
        }
        final List<INonDominationLevel<T>> rs = new ArrayList<>();
        for (INonDominationLevel<T> level : levelsSnapshot) {
            if (level != null) {
                rs.add(level);
            }
        }
        return new PopulationSnapshot<>(rs, sizeSnapshot);
    }

    @Nullable
    IIndividual<T> intRemoveWorst() {
        while (true) {
            try {
                final int lastLevelIndex = nonDominationLevels.size() - 1;
                final LevelRef lastLevelRef = nonDominationLevels.get(lastLevelIndex).get();
                if (lastLevelRef == null) {
                    continue;
                }
                final JFBYNonDominationLevel<T> lastLevel = lastLevelRef.level;
                if (lastLevel.getMembers().size() <= 1) {
                    try {
                        addRemoveLevelLock.lock();
                        if (lastLevelIndex == nonDominationLevels.size() - 1 &&
                                nonDominationLevels.get(lastLevelIndex).compareAndSet(lastLevelRef, null)) {
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                return null;
                            } else {
                                final IIndividual<T> rs = lastLevel.getMembers().get(0);
                                presentIndividuals.remove(rs);
                                return rs;
                            }
                        }
                    } finally {
                        addRemoveLevelLock.unlock();
                    }
                } else {
                    final IIndividual<T> removedIndividual = getWorstCDIndividual(lastLevel);
                    if (removedIndividual == null)
                        return null;

                    final JFBYNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);

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
    public int addIndividual(@Nonnull IIndividual<T> addend) {
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

    private Integer insertOneByOne(@Nonnull final IIndividual<T> originalAddend, int rank, final long ts) {
        Integer firstModifiedLevelRank = null;
        List<IIndividual<T>> addends = new LinkedList<>();
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
                    final JFBYNonDominationLevel<T> level = levelRef.level;
                    if (ts > levelRef.modificationTs) {
                        final INonDominationLevel.MemberAdditionResult<T, JFBYNonDominationLevel<T>> mar = level.addMembers(addends);
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
                        final List<IIndividual<T>> newCurrLevelMembers = new ArrayList<>();
                        final List<IIndividual<T>> nextAddends = new ArrayList<>();
                        final Set<IIndividual<T>> addendsSet = new HashSet<>(addends);
                        final List<IIndividual<T>> actualAddends = new ArrayList<>(addends.size());
                        final Set<IIndividual<T>> actualRemovals = new HashSet<>();
                        for (int i = 0; i < allMembers.length; ++i) {
                            if (ranks[i] == 0) {
                                //noinspection unchecked
                                newCurrLevelMembers.add(allMembers[i]);
                                if (addendsSet.contains(allMembers[i])) {
                                    //noinspection unchecked
                                    actualAddends.add(allMembers[i]);
                                }
                            } else {
                                //noinspection unchecked
                                nextAddends.add(allMembers[i]);
                                if (!addendsSet.contains(allMembers[i])) {
                                    //noinspection unchecked
                                    actualRemovals.add(allMembers[i]);
                                }
                            }
                        }

                        final CrowdingDistanceData<T> cdd = Utils.recalcCrowdingDistances(
                                originalAddend.getObjectives().length,
                                level.getSortedObjectives(),
                                actualAddends,
                                actualRemovals,
                                newCurrLevelMembers
                        );

                        final JFBYNonDominationLevel<T> newLevel = new JFBYNonDominationLevel<>(sorter, cdd.getIndividuals(), cdd.getSortedObjectives());
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

    private boolean tryToAddLevels(List<IIndividual<T>> addends, int minPossibleRank) {
        addRemoveLevelLock.lock();
        try {
            if (minPossibleRank >= nonDominationLevels.size()) {
                final IIndividual[] members = addends.toArray(new IIndividual[addends.size()]);
                final int[] ranks = sorter.performNds(members);
                final List<List<IIndividual<T>>> levels = new ArrayList<>();
                for (int i = 0; i < members.length; ++i) {
                    while (levels.size() <= ranks[i]) {
                        levels.add(new ArrayList<>());
                    }
                    //noinspection unchecked
                    levels.get(ranks[i]).add(members[i]);
                }
                for (List<IIndividual<T>> level : levels) {
                    nonDominationLevels.add(new AtomicReference<>(
                            new LevelRef(time.incrementAndGet(), new JFBYNonDominationLevel<>(sorter, level))));
                }
                return true;
            }
        } finally {
            addRemoveLevelLock.unlock();
        }
        return false;
    }

    private Integer insertViaFullSortings(@Nonnull IIndividual<T> addend, int rank) {
        Integer firstModifiedLevelRank = null;
        List<IIndividual<T>> addends = new ArrayList<>();
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
                    final IIndividual[] allMembers = lexMerge(addends, levelRef.level.getMembers());
                    final int[] ranks = sorter.performNds(allMembers);
                    final List<IIndividual<T>> newCurrLevelMembers = new ArrayList<>();
                    final List<IIndividual<T>> nextAddends = new ArrayList<>();
                    for (int i = 0; i < allMembers.length; ++i) {
                        if (ranks[i] == 0) {
                            //noinspection unchecked
                            newCurrLevelMembers.add(allMembers[i]);
                        } else {
                            //noinspection unchecked
                            nextAddends.add(allMembers[i]);
                        }
                    }

                    final Set<IIndividual<T>> addendsSet = new HashSet<>(addends);
                    final Set<IIndividual<T>> removed = new HashSet<>();
                    for (IIndividual<T> nextAddend : nextAddends) {
                        if (addendsSet.contains(nextAddend)) {
                            addendsSet.remove(nextAddend);
                        } else {
                            removed.add(nextAddend);
                        }
                    }
                    final CrowdingDistanceData<T> cdd = Utils.recalcCrowdingDistances(addend.getObjectives().length,
                            levelRef.level.getSortedObjectives(), new ArrayList<>(addendsSet), removed, newCurrLevelMembers);
                    final JFBYNonDominationLevel<T> newLevel = new JFBYNonDominationLevel<>(sorter, cdd.getIndividuals(), cdd.getSortedObjectives());
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

    private IIndividual[] lexMerge(@Nonnull final List<IIndividual<T>> aList,
                                   @Nonnull final List<IIndividual<T>> mList) {
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
    public CJFBYPopulation<T> clone() {
        final CopyOnWriteArrayList<AtomicReference<JFBYNonDominationLevel<T>>> list = new CopyOnWriteArrayList<>();
        for (AtomicReference<LevelRef> levelRef : nonDominationLevels) {
            list.add(new AtomicReference<>(levelRef.get().level.copy()));
        }
        return new CJFBYPopulation<>(list, expectedPopulationSize, useOneByOneSorting);
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
