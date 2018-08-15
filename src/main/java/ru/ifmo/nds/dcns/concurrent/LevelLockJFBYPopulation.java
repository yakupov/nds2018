package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.QuickSelect;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class LevelLockJFBYPopulation implements IManagedPopulation {
    private static final double DEFAULT_DELETION_THRESHOLD = 1.2;

    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();
    private final Lock removeLevelLock = new ReentrantLock();

    private final JFB2014 sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels;
    private final Map<IIndividual, Boolean> presentIndividuals = new ConcurrentHashMap<>();

    private final long expectedPopSize;
    private final double deletionThreshold;

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation() {
        this(Long.MAX_VALUE);
    }

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation(long expectedPopSize) {
        this(new IncrementalJFB(), new CopyOnWriteArrayList<>(), expectedPopSize, DEFAULT_DELETION_THRESHOLD);
    }

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation(final long expectedPopSize,
                                   final double deletionThreshold) {
        this(new IncrementalJFB(), new CopyOnWriteArrayList<>(), expectedPopSize, deletionThreshold);
    }

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation(@Nonnull final JFB2014 sorter,
                                   @Nonnull final CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels,
                                   final long expectedPopSize,
                                   final double deletionThreshold) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopSize = expectedPopSize;
        this.deletionThreshold = deletionThreshold;

        for (INonDominationLevel level : nonDominationLevels) {
            levelLocks.add(new ReentrantLock());
            size.addAndGet(level.getMembers().size());
            for (IIndividual individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot getSnapshot() {
        final ArrayList<INonDominationLevel> rs = new ArrayList<>(nonDominationLevels);
        return new PopulationSnapshot(rs, rs.stream().mapToInt(l -> l.getMembers().size()).sum());
    }

    @Nonnull
    @Override
    public List<? extends INonDominationLevel> getLevelsUnsafe() {
        return Collections.unmodifiableList(nonDominationLevels);
    }

    @SuppressWarnings("UnusedReturnValue")
    private int massRemoveWorst() {
        if (size.get() > expectedPopSize * deletionThreshold && removeLevelLock.tryLock()) {
            try {
                final int toDelete = (int) (size.get() - expectedPopSize);
                int remaining = toDelete;
                while (remaining > 0) {
                    final int lastLevelIndex = nonDominationLevels.size() - 1;
                    final Lock lastLevelLock;

                    lastLevelLock = acquireLock(lastLevelIndex);
                    addLevelLock.lock();
                    if (lastLevelLock != levelLocks.get(lastLevelIndex)
                            || lastLevelIndex != nonDominationLevels.size() - 1) {
                        lastLevelLock.unlock();
                        addLevelLock.unlock();
                        continue;
                    }

                    try {
                        final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex);
                        if (lastLevel.getMembers().size() <= remaining) {
                            levelLocks.remove(lastLevelIndex);
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                            } else {
                                for (IIndividual individual : lastLevel.getMembers()) {
                                    presentIndividuals.remove(individual);
                                }
                            }
                            remaining -= lastLevel.getMembers().size();
                        } else {
                            final double[] cd = new double[lastLevel.getMembers().size()];
                            int i = 0;
                            for (CDIndividual cdIndividual : lastLevel.getMembersWithCD()) {
                                cd[i++] = cdIndividual.getCrowdingDistance();
                            }
                            final double cdThreshold = new QuickSelect().getKthElement(cd, remaining);
                            final List<IIndividual> newMembers = new ArrayList<>();
                            for (CDIndividual cdIndividual : lastLevel.getMembersWithCD()) {
                                if (remaining > 0 && cdIndividual.getCrowdingDistance() <= cdThreshold) {
                                    presentIndividuals.remove(cdIndividual.getIndividual());
                                    --remaining;
                                } else {
                                    newMembers.add(cdIndividual.getIndividual());
                                }
                            }
                            if (newMembers.isEmpty()) {
                                System.err.println("Empty members generated");
                                System.out.println(cdThreshold);
                                System.out.println(Arrays.toString(cd));
                                throw new RuntimeException(remaining + "<" + lastLevel.getMembers().size());
                            }
                            final JFBYNonDominationLevel newLevel = new JFBYNonDominationLevel(sorter, newMembers); //FIXME: partial recalc CD
                            nonDominationLevels.set(lastLevelIndex, newLevel);
                        }

                    } finally {
                        lastLevelLock.unlock();
                        addLevelLock.unlock();
                    }
                }
                if (toDelete > 0) {
                    int tSize = size.get();
                    while (!size.compareAndSet(tSize, tSize - toDelete)) {
                        tSize = size.get();
                    }
                }
                return toDelete;
            } finally {
                removeLevelLock.unlock();
            }
        }
        return 0;
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return determineRank(addend);
        }

        int rank;
        while (true) {
            rank = determineRank(addend);
            final Lock lock = acquireLock(rank);
            try {
                if (rank < nonDominationLevels.size() &&
                        nonDominationLevels.get(rank).dominatedByAnyPointOfThisLayer(addend)) {
                    lock.unlock();
                } else {
                    // System.out.println(Thread.currentThread().getName() + ": all=" + addLevelLock + ", rl=" + lock);
                    break;
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                lock.unlock();
            }
        }

        //Locked current level or all level addition

        if (rank >= nonDominationLevels.size()) {
            final List<IIndividual> individuals = new ArrayList<>();
            individuals.add(addend);
            final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, individuals); //New level - full CD calc
            levelLocks.add(new ReentrantLock());
            nonDominationLevels.add(level);
            addLevelLock.unlock();
        } else {
            List<IIndividual> addends = Collections.singletonList(addend);
            int i = rank;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                //assertion: we have locked levelLocks.get(i)
                try {
                    final INonDominationLevel level = nonDominationLevels.get(i);
                    final INonDominationLevel.MemberAdditionResult memberAdditionResult = level.addMembers(addends);
                    nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());

                    if (!memberAdditionResult.getEvictedMembers().isEmpty()) {
                        acquireLock(i + 1);
                    }
                    addends = memberAdditionResult.getEvictedMembers();
                } finally {
                    levelLocks.get(i).unlock();
                }

                i++;
            }
            if (!addends.isEmpty()) {
                final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, addends); //New level - full CD calc
                levelLocks.add(new ReentrantLock());
                nonDominationLevels.add(level);
                addLevelLock.unlock();
            }
        }

        size.incrementAndGet();
        massRemoveWorst();

        return rank;
    }

    private Lock acquireLock(int rank) {
        while (true) {
            Lock lock = null;
            try {
                if (rank < nonDominationLevels.size()) {
                    lock = levelLocks.get(rank);
                    lock.lock();
                    if (levelLocks.get(rank) == lock) {
                        return lock;
                    } else {
                        lock.unlock();
                    }
                } else {
                    addLevelLock.lock();
                    if (rank < nonDominationLevels.size()) {
                        addLevelLock.unlock();
                    } else {
                        return addLevelLock;
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public LevelLockJFBYPopulation clone() {
        final LevelLockJFBYPopulation copy = new LevelLockJFBYPopulation(sorter, nonDominationLevels, expectedPopSize, deletionThreshold);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getSnapshot().getLevels().add(level.copy());
        }
        return copy;
    }
}
