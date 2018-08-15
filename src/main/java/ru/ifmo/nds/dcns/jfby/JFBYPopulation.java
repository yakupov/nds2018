package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.ifmo.nds.util.Utils.getWorstCDIndividual;
import static ru.ifmo.nds.util.Utils.removeIndividualFromLevel;

@SuppressWarnings("WeakerAccess")
@NotThreadSafe
public class JFBYPopulation implements IManagedPopulation {
    @Nonnull
    private final List<INonDominationLevel> nonDominationLevels;

    private final Map<IIndividual, Boolean> presentIndividuals = new HashMap<>();

    private final JFB2014 sorter;

    private int lastNumberOfMovements = 0;
    private int lastSumOfMovements = 0;
    private int size = 0;

    private final long expectedPopSize;

    public JFBYPopulation(long expectedPopSize) {
        this(new IncrementalJFB(), expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final List<INonDominationLevel> nonDominationLevels, long expectedPopSize) {
        this(nonDominationLevels, new IncrementalJFB(), expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final JFB2014 sorter, long expectedPopSize) {
        this(new ArrayList<>(), sorter, expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final List<INonDominationLevel> nonDominationLevels,
                          @Nonnull final JFB2014 sorter, long expectedPopSize) {
        this.nonDominationLevels = nonDominationLevels;
        this.sorter = sorter;
        this.expectedPopSize = expectedPopSize;

        for (INonDominationLevel level : nonDominationLevels) {
            size += level.getMembers().size();
            for (IIndividual individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot getSnapshot() {
        return new PopulationSnapshot(Collections.unmodifiableList(nonDominationLevels), size);
    }

    @Nullable
    IIndividual intRemoveWorst() {
        final int lastLevelIndex = nonDominationLevels.size() - 1;
        final INonDominationLevel lastLevel = nonDominationLevels.get(lastLevelIndex);
        if (lastLevel.getMembers().size() <= 1) {
            nonDominationLevels.remove(lastLevelIndex);
            if (lastLevel.getMembers().isEmpty()) {
                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                return null;
            } else {
                --size;
                final IIndividual individual = lastLevel.getMembers().get(0);
                presentIndividuals.remove(individual);
                return individual;
            }
        } else {
            final IIndividual removedIndividual = getWorstCDIndividual(lastLevel);
            final JFBYNonDominationLevel newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);
            nonDominationLevels.set(lastLevelIndex, newLevel);
            presentIndividuals.remove(removedIndividual);
            --size;
            return removedIndividual;
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        lastNumberOfMovements = 0;
        lastSumOfMovements = 0;

        final int rank = determineRank(addend);

        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        } else if (rank >= nonDominationLevels.size()) {
            final List<IIndividual> individuals = new ArrayList<>();
            individuals.add(addend);
            final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, individuals);
            nonDominationLevels.add(level);
        } else {
            List<IIndividual> addends = Collections.singletonList(addend);
            int i = rank;
            int prevSize = -1;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                ++lastNumberOfMovements;
                lastSumOfMovements += addends.size();

                if (prevSize == addends.size()) { //Whole level was pushed
                    final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, addends);
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
                final JFBYNonDominationLevel level = new JFBYNonDominationLevel(sorter, addends);
                nonDominationLevels.add(level);
            }
        }

        if (++size > expectedPopSize) {
            intRemoveWorst();
        }
        return rank;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public JFBYPopulation clone() {
        final JFBYPopulation copy = new JFBYPopulation(nonDominationLevels, sorter, expectedPopSize);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getSnapshot().getLevels().add(level.copy());
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
