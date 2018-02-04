package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.util.ObjectiveComparator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@ThreadSafe
public class SyncJFBYPopulation extends AbstractConcurrentJFBYPopulation {
    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();
    private final Lock removeLevelLock = new ReentrantLock();

    private final Random random = new Random(System.nanoTime());
    private final JFB2014 sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels;

    private final long expectedPopSize;

    @SuppressWarnings("WeakerAccess")
    public SyncJFBYPopulation() {
        this(Long.MAX_VALUE);
    }

    @SuppressWarnings("WeakerAccess")
    public SyncJFBYPopulation(long expectedPopSize) {
        this(new IncrementalJFB(), new CopyOnWriteArrayList<>(), expectedPopSize);
    }

    @SuppressWarnings("WeakerAccess")
    public SyncJFBYPopulation(JFB2014 sorter, CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels, long expectedPopSize) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;
        this.expectedPopSize = expectedPopSize;

        for (INonDominationLevel level : nonDominationLevels) {
            levelLocks.add(new ReentrantLock());
            size.addAndGet(level.getMembers().size());
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
                removeLevelLock.unlock();

                try {
                    if (lastLevelIndex == nonDominationLevels.size() - 1) {
                        final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex);
                        if (lastLevel.getMembers().size() <= 1) {
                            levelLocks.remove(lastLevelIndex);
                            nonDominationLevels.remove(lastLevelIndex);
                            if (lastLevel.getMembers().isEmpty()) {
                                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                            } else {
                                size.decrementAndGet();
                                return lastLevel.getMembers().get(0);
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
                            nonDominationLevels.set(lastLevelIndex, new JFBYNonDominationLevel(sorter, newMembers));
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
            }
        }
    }

    @Nonnull
    @Override
    public List<IIndividual> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }
        final int actualCount = Math.min(count, size());
        return random.ints(0, size.get(), actualCount * 3).distinct().limit(actualCount).mapToObj(i -> {
                    for (INonDominationLevel level : nonDominationLevels) {
                        if (i < level.getMembers().size()) {
                            return level.getMembers().get(i);
                        } else {
                            i -= level.getMembers().size();
                        }
                    }
                    throw new RuntimeException("Failed to get individual number " + i + ", levels: " + nonDominationLevels);
                }
        ).collect(Collectors.toList());
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
            if (!nonDominationLevels.get(test).dominatedByAnyPointOfThisLayer(point)) {
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
        int rank = 0;
        boolean locked = false;
        while (!locked) {
            rank = determineRank(addend);
            if (rank < nonDominationLevels.size()) {
                final Lock rankLock = levelLocks.get(rank);
                rankLock.lock();
                if (nonDominationLevels.get(rank).dominatedByAnyPointOfThisLayer(addend)) {
                    rankLock.unlock();
                } else {
                    locked = true;
                }
            } else {
                addLevelLock.lock();
                if (determineRank(addend) < nonDominationLevels.size()) {
                    addLevelLock.unlock();
                } else {
                    locked = true;
                }
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
                        if (i < nonDominationLevels.size() - 1) {
                            levelLocks.get(i + 1).lock();
                        } else {
                            addLevelLock.lock();
                            if (i < nonDominationLevels.size() - 1) {
                                addLevelLock.unlock();
                                levelLocks.get(i + 1).lock();
                            }
                        }
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

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public SyncJFBYPopulation clone() {
        final SyncJFBYPopulation copy = new SyncJFBYPopulation(sorter, nonDominationLevels, expectedPopSize);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getLevels().add(level.copy());
        }
        return copy;
    }
}
