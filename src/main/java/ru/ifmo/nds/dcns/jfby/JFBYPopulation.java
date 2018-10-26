package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.sorter.IncrementalJFB;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.util.SortedObjectives;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.*;

import static ru.ifmo.nds.util.Utils.getWorstCDIndividual;
import static ru.ifmo.nds.util.Utils.removeIndividualFromLevel;

@SuppressWarnings("WeakerAccess")
@NotThreadSafe
public class JFBYPopulation<T> implements IManagedPopulation<T> {
    @Nonnull
    private final List<JFBYNonDominationLevel<T>> nonDominationLevels;

    private final Map<IIndividual<T>, Boolean> presentIndividuals = new HashMap<>();

    private final JFB2014 sorter;

    private int lastNumberOfMovements = 0;
    private int lastSumOfMovements = 0;
    private int size = 0;

    private final long expectedPopSize;

    public JFBYPopulation(long expectedPopSize) {
        this(new IncrementalJFB(), expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final List<JFBYNonDominationLevel<T>> nonDominationLevels, long expectedPopSize) {
        this(nonDominationLevels, new IncrementalJFB(), expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final JFB2014 sorter, long expectedPopSize) {
        this(new ArrayList<>(), sorter, expectedPopSize);
    }

    public JFBYPopulation(@Nonnull final List<JFBYNonDominationLevel<T>> nonDominationLevels,
                          @Nonnull final JFB2014 sorter, long expectedPopSize) {
        this.nonDominationLevels = nonDominationLevels;
        this.sorter = sorter;
        this.expectedPopSize = expectedPopSize;

        for (INonDominationLevel<T> level : nonDominationLevels) {
            size += level.getMembers().size();
            for (IIndividual<T> individual : level.getMembers()) {
                presentIndividuals.put(individual, true);
            }
        }
    }

    @Override
    @Nonnull
    public PopulationSnapshot<T> getSnapshot() {
        return new PopulationSnapshot<>(Collections.unmodifiableList(nonDominationLevels), size);
    }

    @Nullable
    IIndividual<T> intRemoveWorst() {
        final int lastLevelIndex = nonDominationLevels.size() - 1;
        final JFBYNonDominationLevel<T> lastLevel = nonDominationLevels.get(lastLevelIndex);
        if (lastLevel.getMembers().size() <= 1) {
            nonDominationLevels.remove(lastLevelIndex);
            if (lastLevel.getMembers().isEmpty()) {
                System.err.println("Empty last ND level! Levels = " + nonDominationLevels);
                return null;
            } else {
                --size;
                final IIndividual<T> individual = lastLevel.getMembers().get(0);
                presentIndividuals.remove(individual);
                return individual;
            }
        } else {
            final IIndividual<T> removedIndividual = getWorstCDIndividual(lastLevel);
            if (removedIndividual == null) {
                return null;
            }
            final JFBYNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual, sorter);
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
    public int addIndividual(@Nonnull IIndividual<T> addend) {
        lastNumberOfMovements = 0;
        lastSumOfMovements = 0;

        final int rank = determineRank(addend);

        if (presentIndividuals.putIfAbsent(addend, true) != null) {
            return rank;
        } else if (rank >= nonDominationLevels.size()) {
            final List<IIndividual<T>> individuals = Collections.singletonList(addend);
            final JFBYNonDominationLevel<T> level = new JFBYNonDominationLevel<>(
                    sorter,
                    individuals
            );
            nonDominationLevels.add(level);
        } else {
            List<IIndividual<T>> addends = Collections.singletonList(addend);
            int i = rank;
            int prevSize = -1;
            SortedObjectives<IIndividual<T>, T> prevSortedObjectives = null;
            while (!addends.isEmpty() && i < nonDominationLevels.size()) {
                ++lastNumberOfMovements;
                lastSumOfMovements += addends.size();

                if (prevSize == addends.size()) { //Whole level was pushed
                    final JFBYNonDominationLevel<T> level = new JFBYNonDominationLevel<>(sorter, addends, prevSortedObjectives);
                    nonDominationLevels.add(i, level);
                    return rank;
                }

                final JFBYNonDominationLevel<T> level = nonDominationLevels.get(i);
                prevSize = level.getMembers().size();
                prevSortedObjectives = level.getSortedObjectives();
                final INonDominationLevel.MemberAdditionResult<T, JFBYNonDominationLevel<T>> memberAdditionResult = level.addMembers(addends);
                nonDominationLevels.set(i, memberAdditionResult.getModifiedLevel());
                addends = memberAdditionResult.getEvictedMembers();
                i++;
            }
            if (!addends.isEmpty()) {
                final JFBYNonDominationLevel<T> level = new JFBYNonDominationLevel<>(sorter, addends);
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
    public JFBYPopulation<T> clone() {
        final JFBYPopulation<T> copy = new JFBYPopulation<>(nonDominationLevels, sorter, expectedPopSize);
        for (INonDominationLevel<T> level : nonDominationLevels) {
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
