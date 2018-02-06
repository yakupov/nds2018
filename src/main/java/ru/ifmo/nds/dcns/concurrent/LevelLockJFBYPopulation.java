package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.QuickSelect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ru.ifmo.nds.util.Utils.getWorstCDIndividual;
import static ru.ifmo.nds.util.Utils.removeIndividualFromLevel;

@ThreadSafe
public class LevelLockJFBYPopulation extends AbstractConcurrentJFBYPopulation {
    private static final double DELETION_THRESHOLD = 1.2;

    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();
    private final Lock removeLevelLock = new ReentrantLock();

    private final JFB2014 sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels;
    private final Map<IIndividual, Boolean> presentIndividuals = new ConcurrentHashMap<>();

    private final long expectedPopSize;

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation() {
        this(Long.MAX_VALUE);
    }

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation(long expectedPopSize) {
        this(new IncrementalJFB(), new CopyOnWriteArrayList<>(), expectedPopSize);
    }

    @SuppressWarnings("WeakerAccess")
    public LevelLockJFBYPopulation(JFB2014 sorter, CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels, long expectedPopSize) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopSize = expectedPopSize;

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
    public List<INonDominationLevel> getLevels() {
        return new ArrayList<>(nonDominationLevels);
    }

    @Nullable
    @Override
    public IIndividual removeWorst() {
        throw new UnsupportedOperationException("Explicit deletion of members not allowed");
    }

    private int massRemoveWorst() {
        if (size.get() > expectedPopSize * DELETION_THRESHOLD && removeLevelLock.tryLock()) {
            try {
                final int toDelete = (int) (size.get() - expectedPopSize * DELETION_THRESHOLD);
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
                            final JFBYNonDominationLevel newLevel = new JFBYNonDominationLevel(sorter, newMembers);
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

    @SuppressWarnings("unused")
    @Nullable
    IIndividual intRemoveWorst() {
        while (true) {
            //removeLevelLock.lock();
            final int lastLevelIndex = nonDominationLevels.size() - 1;
            final Lock lastLevelLock;
//            try {
            lastLevelLock = acquireLock(lastLevelIndex);
            addLevelLock.lock();
            try {
                if (lastLevelLock != levelLocks.get(lastLevelIndex)
                        || lastLevelIndex != nonDominationLevels.size() - 1) {
                    lastLevelLock.unlock();
                    addLevelLock.unlock();
                    continue;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                lastLevelLock.unlock();
                addLevelLock.unlock();
                continue;
            }
//            } finally {
//                removeLevelLock.unlock();
//            }
            try {
                try {
                    final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex);
                    if (lastLevel.getMembers().size() <= 1) {
                        levelLocks.remove(lastLevelIndex);
                        nonDominationLevels.remove(lastLevelIndex);
                        if (lastLevel.getMembers().isEmpty()) {
                            System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                            return null;
                        } else {
                            size.decrementAndGet();
                            final IIndividual individual = lastLevel.getMembers().get(0);
                            presentIndividuals.remove(individual);
                            return individual;
                        }
                    } else {
                        final IIndividual removedIndividual = getWorstCDIndividual(lastLevel);
                        final JFBYNonDominationLevel newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);
                        nonDominationLevels.set(lastLevelIndex, newLevel);
                        presentIndividuals.remove(removedIndividual);
                        size.decrementAndGet();
                        return removedIndividual;
                    }

                } finally {
                    lastLevelLock.unlock();
                    addLevelLock.unlock();
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                //retry
            }
        }
    }

    @Nullable
        //@Override
    IIndividual intRemoveWorst11() {
        while (true) {
            removeLevelLock.lock();
            try {
                final int lastLevelIndex = nonDominationLevels.size() - 1;
                final Lock lastLevelLock = levelLocks.get(lastLevelIndex);

                lastLevelLock.lock();
                addLevelLock.lock();

                try {
                    if (lastLevelIndex == nonDominationLevels.size() - 1) {
                        final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex);
                        if (lastLevel.getMembers().size() <= 1) {
                            levelLocks.remove(lastLevelIndex);
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                                return null;
                            } else {
                                size.decrementAndGet();
                                final IIndividual individual = lastLevel.getMembers().get(0);
                                presentIndividuals.remove(individual);
                                return individual;
                            }
                        } else {
                            final IIndividual removedIndividual = getWorstCDIndividual(lastLevel);
                            final JFBYNonDominationLevel newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);
                            nonDominationLevels.set(lastLevelIndex, newLevel);
                            presentIndividuals.remove(removedIndividual);
                            size.decrementAndGet();
                            return removedIndividual;
                        }
                    }
                } finally {
                    lastLevelLock.unlock();
                    addLevelLock.unlock();
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                //retry
            } finally {
                removeLevelLock.unlock(); //TODO: move back to method
            }
        }
    }

    @Nonnull
    @Override
    public List<CDIndividual> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }

        final List<INonDominationLevel> levelsSnapshot = getLevels();
        int popSize = 0;
        for (INonDominationLevel level : levelsSnapshot) {
            popSize += level.getMembers().size();
        }
        final int actualCount = Math.min(count, popSize);
        final int[] indices = ThreadLocalRandom.current()
                .ints(actualCount * 3, 0, popSize)
                .distinct().sorted().limit(actualCount).toArray();
        final List<CDIndividual> res = new ArrayList<>();
        int i = 0;
        int prevLevelsSizeSum = 0;
        for (INonDominationLevel level : levelsSnapshot) {
            final int levelSize = level.getMembers().size();
            while (i < actualCount && indices[i] - prevLevelsSizeSum < levelSize) {
                //System.out.println(level.getMembers().size() + "==" + level.getMembersWithCD().size());
                res.add(level.getMembersWithCD().get(indices[i] - prevLevelsSizeSum));
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

    private int determineRank(IIndividual point) {
        int l = 0;
        int r = nonDominationLevels.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            try {
                if (!nonDominationLevels.get(test).dominatedByAnyPointOfThisLayer(point)) {
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
            final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, individuals);
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
                final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, addends);
                levelLocks.add(new ReentrantLock());
                nonDominationLevels.add(level);
                addLevelLock.unlock();
            }
        }

//        if (size.incrementAndGet() > expectedPopSize) {
//            intRemoveWorst();
//        }
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
        final LevelLockJFBYPopulation copy = new LevelLockJFBYPopulation(sorter, nonDominationLevels, expectedPopSize);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getLevels().add(level.copy());
        }
        return copy;
    }
}
