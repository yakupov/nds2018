package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.jfby.JFBYNonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ThreadSafe
public class CJFBYPopulation implements IManagedPopulation {
    private final List<INonDominationLevel> nonDominationLevels = new ArrayList<>();
    private final JFB2014 sorter;

    private volatile int lastNumberOfMovements = 0;
    private volatile int lastSumOfMovements = 0;
    private final AtomicInteger size = new AtomicInteger(0);

    public CJFBYPopulation(JFB2014 sorter) {
        this.sorter = sorter;
    }

    @Override
    @Nonnull
    public List<INonDominationLevel> getLevels() {
        return nonDominationLevels;
    }

    @Nullable
    @Override
    public IIndividual removeWorst() {
        throw new UnsupportedOperationException("Not needed to test NDS"); //TODO: impl later, considering CD
    }

    @Nonnull
    @Override
    //FIXME: NOT THREAD SAFE
    public List<IIndividual> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }
        final Random random = ThreadLocalRandom.current();
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

    //FIXME: not thread safe
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
    //FIXME: not thread safe
    public int addIndividual(IIndividual addend) {
        lastNumberOfMovements = 0;
        lastSumOfMovements = 0;

        final int rank = determineRank(addend);
        if (rank >= nonDominationLevels.size()) {
            final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter);
            level.getMembers().add(addend);
            nonDominationLevels.add(level);
        } else {
            List<IIndividual> addends = Collections.singletonList(addend);
            int i = rank;
            int prevSize = -1;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                ++lastNumberOfMovements;
                lastSumOfMovements += addends.size();

                if (prevSize == addends.size()) { //Whole level was pushed
                    final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter);
                    level.getMembers().addAll(addends);
                    nonDominationLevels.add(i, level);
                    return rank;
                }

                final INonDominationLevel level = nonDominationLevels.get(i);
                prevSize = level.getMembers().size();
                final INonDominationLevel.MemberAdditionResult memberAdditionResult = level.addMembers(addends);
                nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
                addends = memberAdditionResult.getEvictedMembers();
                i++;
            }
            if (!addends.isEmpty()) {
                final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter);
                level.getMembers().addAll(addends);
                nonDominationLevels.add(level);
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
    //FIXME: not thread safe. RW lock needed?
    public CJFBYPopulation clone() {
        final CJFBYPopulation copy = new CJFBYPopulation(sorter);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getLevels().add(level.copy());
        }
        return copy;
    }

    @Override
    public String toString() {
        return "JFBYPopulation{" + "nonDominationLevels=" + nonDominationLevels +
                ", lastNumberOfMovements=" + lastNumberOfMovements +
                ", lastSumOfMovements=" + lastSumOfMovements +
                '}';
    }
}
