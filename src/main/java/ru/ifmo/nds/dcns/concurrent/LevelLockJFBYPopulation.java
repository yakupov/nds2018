package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;

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

    @Nullable
    @Override
    IIndividual intRemoveWorst() {
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

    @SuppressWarnings("Duplicates")
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

        if (size.incrementAndGet() > expectedPopSize) {
            intRemoveWorst();
        }

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
