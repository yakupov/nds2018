package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;
import ru.itmo.nds.util.RankedIndividual;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public interface IManagedPopulation extends Cloneable {
    /**
     * @param individual Individual to add
     * @return Added individual's rank (starting from zero)
     */
    int addIndividual(@Nonnull IIndividual individual);

    /**
     * @return List of non-domination levels. Index in list equals to layer's rank.
     */
    @Nonnull
    List<? extends INonDominationLevel> getLevels();

    /**
     * @return Removed individual. Null if the population was empty.
     */
    @Nullable
    IIndividual removeWorst();

    /**
     * @param count max. number of solutions to return
     * @return list with min(population size, count) random solutions
     */
    @Nonnull
    List<CDIndividual> getRandomSolutions(int count);

    int size();

    IManagedPopulation clone();

    default RankedPopulation<IIndividual> toRankedPopulation() {
        final int popSize = size();

        final IIndividual[] pop;
        final int[] sortedRanks;
        if (popSize > 0) {
            //noinspection unchecked
            pop = new IIndividual[popSize];
            final int[] ranks = new int[popSize];
            int j = 0;
            for (int i = 0; i < getLevels().size(); ++i) {
                for (IIndividual d : getLevels().get(i).getMembers()) {
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
}
