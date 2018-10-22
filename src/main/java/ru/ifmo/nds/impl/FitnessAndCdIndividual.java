package ru.ifmo.nds.impl;

import ru.ifmo.nds.IIndividual;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Arrays;

@ThreadSafe
public class FitnessAndCdIndividual<T> implements IIndividual<T> {
    private final double[] objectives;

    private final double crowdingDistance;

    private final T payload;

    private final int hashCode;

    public FitnessAndCdIndividual(double[] objectives, T payload) {
        this(objectives, 0, payload);
    }

    public FitnessAndCdIndividual(double[] objectives, double crowdingDistance, T payload) {
        this.objectives = objectives;
        this.crowdingDistance = crowdingDistance;
        this.payload = payload;
        this.hashCode = Arrays.hashCode(objectives);
    }

    @Nonnull
    @Override
    public double[] getObjectives() {
        return objectives;
    }

    @Override
    public double getCrowdingDistance() {
        return crowdingDistance;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FitnessAndCdIndividual that = (FitnessAndCdIndividual) o;
        return Arrays.equals(objectives, that.objectives);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "FitnessAndCdIndividual{" +
                "objectives=" + Arrays.toString(objectives) +
                ", crowdingDistance=" + crowdingDistance +
                '}';
    }
}
