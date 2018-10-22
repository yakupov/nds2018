package ru.ifmo.nds;

import javax.annotation.Nonnull;

public interface IIndividual<T> {
    @Nonnull
    double[] getObjectives();

    double getCrowdingDistance();

    T getPayload();
}
