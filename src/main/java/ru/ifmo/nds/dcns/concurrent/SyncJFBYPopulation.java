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

    @SuppressWarnings("unused") //TODO: delete later
    private int getRankSyncSpin(@Nonnull IIndividual addend) {
        int rank;
        while (true) {
            try {
                rank = determineRank(addend);
                final Lock lockedLock;
                if (rank >= nonDominationLevels.size()) {
                    lockedLock = addLevelLock;
                } else {
                    lockedLock = levelLocks.get(rank);
                }
                if (lockedLock.tryLock()) {
                    if (rank == determineRank(addend)) {
                        break;
                    } else {
                        lockedLock.unlock();
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
        return rank;
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        int rank = 0;

        boolean locked = false;
        while (!locked) {
            addLevelLock.lock();
            synchronized (levelLocks) {
                rank = determineRank(addend);
                //noinspection SimplifiableIfStatement
                if (rank >= nonDominationLevels.size()) {
                    locked = true;
                } else {
                    locked = levelLocks.get(rank).tryLock();
                    if (locked && nonDominationLevels.get(rank).dominatedByAnyPointOfThisLayer(addend)) {
                        locked = false;
                        levelLocks.get(rank).unlock();
                    }
                }
                if (!locked || rank < nonDominationLevels.size()) {
                    addLevelLock.unlock();
                }
            }
        }

        //Locked current level or all level addition

        if (rank >= nonDominationLevels.size()) {
            final List<IIndividual> individuals = new ArrayList<>();
            individuals.add(addend);
            final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, individuals);
            nonDominationLevels.add(level);
            levelLocks.add(new ReentrantLock());
            addLevelLock.unlock();
        } else {
            List<IIndividual> addends = Collections.singletonList(addend);
            int i = rank;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                //assertion: we have locked levelLocks.get(i)
                final INonDominationLevel level = nonDominationLevels.get(i);
                final INonDominationLevel.MemberAdditionResult memberAdditionResult = level.addMembers(addends);
                nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());

                if (i < nonDominationLevels.size() - 1) {
                    levelLocks.get(i + 1).lock();
                } else {
                    addLevelLock.lock();
                    if (i < nonDominationLevels.size() - 1) {
                        levelLocks.get(i + 1).lock();
                        addLevelLock.unlock();
                    }
                }

                levelLocks.get(i).unlock();
                addends = memberAdditionResult.getEvictedMembers();
                i++;
            }
            if (!addends.isEmpty()) {
                final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, addends);
                nonDominationLevels.add(level);
                levelLocks.add(new ReentrantLock());
                addLevelLock.unlock();
            } else {
                if (i < nonDominationLevels.size()) {
                    levelLocks.get(i).unlock();
                } else {
                    addLevelLock.unlock();
                }
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
