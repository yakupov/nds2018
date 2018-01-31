package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@ThreadSafe
public class SyncJFBYPopulation implements IManagedPopulation {
    private final List<Lock> levelLocks = new CopyOnWriteArrayList<>();
    private final Lock addLevelLock = new ReentrantLock();

    private final Random random = new Random(System.nanoTime());
    private final JFB2014 sorter;

    private final AtomicInteger size = new AtomicInteger(0);
    private final CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels;

    @SuppressWarnings("WeakerAccess")
    public SyncJFBYPopulation() {
        this(new IncrementalJFB(), new CopyOnWriteArrayList<>());
    }

    @SuppressWarnings("WeakerAccess")
    public SyncJFBYPopulation(JFB2014 sorter, CopyOnWriteArrayList<INonDominationLevel> nonDominationLevels) {
        this.sorter = sorter;
        this.nonDominationLevels = nonDominationLevels;

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
        throw new UnsupportedOperationException("Not needed to test NDS"); //TODO: impl later, considering CD
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

        size.incrementAndGet();
        return rank;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public SyncJFBYPopulation clone() {
        final SyncJFBYPopulation copy = new SyncJFBYPopulation(sorter, nonDominationLevels);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getLevels().add(level.copy());
        }
        return copy;
    }
}
