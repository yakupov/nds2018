package ru.ifmo.nds;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IManagedPopulation extends Cloneable {
    /**
     * @param individual Individual to add
     * @return Added individual's rank (starting from zero)
     */
    int addIndividual(IIndividual individual);

    /**
     * @return List of non-domination levels. Index in list equals to layer's rank.
     */
    @Nonnull
    List<INonDominationLevel> getLevels();

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
    List<IIndividual> getRandomSolutions(int count);

    int size();

    IManagedPopulation clone();
}
