package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;
import ru.ifmo.nds.util.QuickSelect;
import ru.ifmo.nds.util.SortedObjectives;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class LevelLockJFBYPopulation<T> implements IManagedPopulation<T> {
    private static final double DEFAULT_DELETION_THRESHOLD = 1.2;

    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();
    private final Lock removeLevelLock = new ReentrantLock();

    private final JFB2014 sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<JFBYNonDominationLevel<T>> nonDominationLevels;
    private final Map<IIndividual<T>, Boolean> presentIndividuals = new ConcurrentHashMap<>();

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
                                   @Nonnull final CopyOnWriteArrayList<JFBYNonDominationLevel<T>> nonDominationLevels,
                                   final long expectedPopSize,
                                   final double deletionThreshold) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopSize = expectedPopSize;
        this.deletionThreshold = deletionThreshold;

        for (INonDominationLevel<T> level : nonDominationLevels) {
            levelLocks.add(new ReentrantLock());
            size.addAndGet(level.getMembers().size());
            for (IIndividual<T> individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        final ArrayList<INonDominationLevel<T>> rs = new ArrayList<>(nonDominationLevels);
        int size = 0;
        for (INonDominationLevel<T> level : rs) {
            size += level.getMembers().size();
        }
        return new PopulationSnapshot<>(rs, size);
    }

    @Nonnull
    @Override
    public List<? extends INonDominationLevel<T>> getLevelsUnsafe() {
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
                        final JFBYNonDominationLevel<T> lastLevel = nonDominationLevels.get(lastLevelIndex);
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
                            for (IIndividual cdIndividual : lastLevel.getMembers()) {
                                cd[i++] = cdIndividual.getCrowdingDistance();
                            }
                            final double cdThreshold = new QuickSelect().getKthElement(cd, remaining);
                            final List<IIndividual<T>> newMembers = new ArrayList<>();
                            final List<IIndividual<T>> removals = new ArrayList<>();
                            for (IIndividual<T> individual : lastLevel.getMembers()) {
                                if (remaining > 0 && individual.getCrowdingDistance() <= cdThreshold) {
                                    presentIndividuals.remove(individual);
                                    removals.add(individual);
                                    --remaining;
                                } else {
                                    newMembers.add(individual);
                                }
                            }
                            if (newMembers.isEmpty()) {
                                System.err.println("Empty members generated");
                                System.out.println(cdThreshold);
                                System.out.println(Arrays.toString(cd));
                                throw new RuntimeException(remaining + "<" + lastLevel.getMembers().size());
                            }

//                            System.err.println("Level: " + lastLevel.getMembers());
//                            System.err.println("Removin': " + removals);

                            final SortedObjectives<IIndividual<T>, T> nso = lastLevel.getSortedObjectives().update(
                                    Collections.emptyList(),
                                    removals,
                                    (i1, d) -> new FitnessAndCdIndividual<>(i1.getObjectives(), d, i1.getPayload())
                            );

                            final JFBYNonDominationLevel<T> newLevel = new JFBYNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso);
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
    public int addIndividual(@Nonnull IIndividual<T> addend) {
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
            final List<IIndividual<T>> individuals = Collections.singletonList(addend);
            final JFBYNonDominationLevel<T> level = new JFBYNonDominationLevel<>(
                    sorter,
                    individuals
            );
            levelLocks.add(new ReentrantLock());
            nonDominationLevels.add(level);
            addLevelLock.unlock();
        } else {
            List<IIndividual<T>> addends = Collections.singletonList(addend);
            int i = rank;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                //assertion: we have locked levelLocks.get(i)
                try {
                    final JFBYNonDominationLevel<T> level = nonDominationLevels.get(i);
                    final INonDominationLevel.MemberAdditionResult<T, JFBYNonDominationLevel<T>> memberAdditionResult = level.addMembers(addends);
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
                levelLocks.add(new ReentrantLock());
                final JFBYNonDominationLevel<T> level = new JFBYNonDominationLevel<>(sorter, addends); //New level - full CD calc
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
    public LevelLockJFBYPopulation<T> clone() {
        final LevelLockJFBYPopulation<T> copy = new LevelLockJFBYPopulation<>(sorter, nonDominationLevels, expectedPopSize, deletionThreshold);
        for (INonDominationLevel<T> level : nonDominationLevels) {
            copy.getSnapshot().getLevels().add(level.copy());
        }
        return copy;
    }
}
