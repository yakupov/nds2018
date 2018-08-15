package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.impl.CDIndividualWithRank;
import ru.ifmo.nds.util.AscLexSortComparator;
import ru.itmo.nds.util.RankedIndividual;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public interface IManagedPopulation extends Cloneable {
    /**
     * @param individual Individual to add
     * @return Added individual's rank (starting from zero)
     */
    int addIndividual(@Nonnull IIndividual individual);

    /**
     * @return Some valid state of non-domination levels. The returned value is guaranteed to persist, no other thread
     * should alter it. Index in list equals to layer's rank.
     */
    @Nonnull
    PopulationSnapshot getSnapshot();

    /**
     * @return Working set of non-domination levels. The returned value cannot be altered directly, but may be
     * modified by some other thread at any time. Index in list equals to layer's rank.
     */
    @Nonnull
    default List<? extends INonDominationLevel> getLevelsUnsafe() {
        return getSnapshot().getLevels();
    }

    int size();

    IManagedPopulation clone();

    /**
     * @param count max. number of solutions to return
     * @return list with min(population size, count) random solutions
     */
    @Nonnull
    default List<CDIndividualWithRank> getRandomSolutions(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative number of random solutions requested");
        }

        final PopulationSnapshot popSnap = getSnapshot();

        final int actualCount = Math.min(count, popSnap.getSize());
        final int[] indices = ThreadLocalRandom.current()
                .ints(actualCount * 3, 0, popSnap.getSize())
                .distinct().sorted().limit(actualCount).toArray();
        final List<CDIndividualWithRank> res = new ArrayList<>();
        int i = 0;
        int prevLevelsSizeSum = 0;
        int rank = 0;
        for (INonDominationLevel level : popSnap.getLevels()) {
            final int levelSize = level.getMembers().size();
            while (i < actualCount && indices[i] - prevLevelsSizeSum < levelSize) {
                final CDIndividual cdIndividual = level.getMembersWithCD().get(indices[i] - prevLevelsSizeSum);
                res.add(new CDIndividualWithRank(cdIndividual.getIndividual(), cdIndividual.getCrowdingDistance(), rank));
                ++i;
            }
            prevLevelsSizeSum += levelSize;
            rank++;
        }

        return res;
    }

    default int determineRank(IIndividual point) {
        final List<? extends INonDominationLevel> ndLayers = getLevelsUnsafe();

        int l = 0;
        int r = ndLayers.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            if (!ndLayers.get(test).dominatedByAnyPointOfThisLayer(point)) {
                lastNonDominating = test;
                r = test - 1;
            } else {
                l = test + 1;
            }
        }

        return lastNonDominating;
    }

    default RankedPopulation<IIndividual> toRankedPopulation() {
        final PopulationSnapshot popSnap = getSnapshot();

        final IIndividual[] pop;
        final int[] sortedRanks;
        if (popSnap.getSize() > 0) {
            //noinspection unchecked
            pop = new IIndividual[popSnap.getSize()];
            final int[] ranks = new int[popSnap.getSize()];
            int j = 0;
            for (int i = 0; i < popSnap.getLevels().size(); ++i) {
                for (IIndividual d : popSnap.getLevels().get(i).getMembers()) {
                    pop[j] = d;
                    ranks[j] = i;
                    j++;
                }
            }

            sortedRanks = RankedIndividual.sortRanksForLexSortedPopulation(ranks, pop, IIndividual::getObjectives);

            Arrays.sort(pop, AscLexSortComparator.getInstance());
        } else {
            pop = null;
            sortedRanks = null;
        }
        return new RankedPopulation<>(pop, sortedRanks);
    }
}
