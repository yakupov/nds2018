package ru.ifmo.nds.dcns.lppsn;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.IncrementalPPSN;
import ru.ifmo.nds.dcns.sorter.PPSN2014;
import ru.itmo.nds.util.RankedIndividual;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class LPPSNPopulation implements IManagedPopulation {
    private final List<INonDominationLevel> nonDominationLevels = new ArrayList<>();
    private final Random random = new Random(System.nanoTime());
    private final PPSN2014 sorter;

    private int lastNumberOfMovements = 0;
    private int lastSumOfMovements = 0;
    private int size = 0;

    public LPPSNPopulation() {
        this(new IncrementalPPSN());
    }

    @SuppressWarnings("WeakerAccess")
    public LPPSNPopulation(PPSN2014 sorter) {
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
    public List<IIndividual> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }
        final int actualCount = Math.min(count, size());
        return random.ints(0, size, actualCount * 3).distinct().limit(actualCount).mapToObj(i -> {
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
        return size;
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

    @Override
    public int addIndividual(IIndividual addend) {
        lastNumberOfMovements = 0;
        lastSumOfMovements = 0;

        final int rank = determineRank(addend);
        if (rank >= nonDominationLevels.size()) {
            final LPPSNNonDominationLevel level = new LPPSNNonDominationLevel(sorter);
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
                    final LPPSNNonDominationLevel level = new LPPSNNonDominationLevel(sorter);
                    level.getMembers().addAll(addends);
                    nonDominationLevels.add(i, level);
                    return rank;
                }

                final INonDominationLevel level = nonDominationLevels.get(i);
                prevSize = level.getMembers().size();
                addends = level.addMembers(addends);
                i++;
            }
            if (!addends.isEmpty()) {
                final LPPSNNonDominationLevel level = new LPPSNNonDominationLevel(sorter);
                level.getMembers().addAll(addends);
                nonDominationLevels.add(level);
            }
        }

        ++size;
        return rank;
    }

    //TODO: create beautiful interface for future tests
    public RankedPopulation<IIndividual> toRankedPopulation() {
        final int popSize = nonDominationLevels.stream().mapToInt(level -> level.getMembers().size()).sum();

        final IIndividual[] pop;
        final int[] sortedRanks;
        if (popSize > 0) {
            //noinspection unchecked
            pop = new IIndividual[popSize];
            final int[] ranks = new int[popSize];
            int j = 0;
            for (int i = 0; i < nonDominationLevels.size(); ++i) {
                for (IIndividual d : nonDominationLevels.get(i).getMembers()) {
                    pop[j] = d;
                    ranks[j] = i;
                    j++;
                }
            }

            sortedRanks = RankedIndividual.sortRanksForLexSortedPopulation(ranks, pop, IIndividual::getObjectives);

            Arrays.sort(pop, (o1, o2) -> {
                final double[] o1Obj = o1.getObjectives();
                final double[] o2Obj = o2.getObjectives();
                for (int i = 0; i < o1Obj.length; ++i) {
                    if (o1Obj[i] < o2Obj[i])
                        return -1;
                    else if (o1Obj[i] > o2Obj[i])
                        return 1;
                }
                return 0;
            });
        } else {
            pop = null;
            sortedRanks = null;
        }
        return new RankedPopulation<>(pop, sortedRanks);
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public LPPSNPopulation clone() {
        final LPPSNPopulation copy = new LPPSNPopulation(sorter);
        for (INonDominationLevel level : nonDominationLevels) {
            copy.getLevels().add(level.copy());
        }
        return copy;
    }

    @Override
    public String toString() {
        return "LPPSNPopulation{" + "nonDominationLevels=" + nonDominationLevels +
                ", lastNumberOfMovements=" + lastNumberOfMovements +
                ", lastSumOfMovements=" + lastSumOfMovements +
                '}';
    }
}
