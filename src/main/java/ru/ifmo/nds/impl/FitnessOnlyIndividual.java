package ru.ifmo.nds.impl;

import ru.ifmo.nds.IIndividual;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Arrays;

@ThreadSafe
@Immutable
public class FitnessOnlyIndividual implements IIndividual {
    @Nonnull
    private final double[] fitness;

    public FitnessOnlyIndividual(@Nonnull double[] fitness) {
        this.fitness = fitness;
    }

    @Nonnull
    @Override
    public double[] getObjectives() {
        return fitness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FitnessOnlyIndividual that = (FitnessOnlyIndividual) o;
        return Arrays.equals(fitness, that.fitness);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(fitness);
    }

    @Override
    public String toString() {
        return Arrays.toString(fitness);
    }
}
