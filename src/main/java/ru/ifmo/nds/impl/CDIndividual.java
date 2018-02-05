package ru.ifmo.nds.impl;

import ru.ifmo.nds.IIndividual;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;

@ThreadSafe
@Immutable
public class CDIndividual {
    @Nonnull
    private final IIndividual individual;
    private final double crowdingDistance;

    public CDIndividual(@Nonnull IIndividual individual, double crowdingDistance) {
        this.individual = individual;
        this.crowdingDistance = crowdingDistance;
    }

    @Nonnull
    public IIndividual getIndividual() {
        return individual;
    }

    public double getCrowdingDistance() {
        return crowdingDistance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CDIndividual that = (CDIndividual) o;
        return Double.compare(that.crowdingDistance, crowdingDistance) == 0 &&
                Objects.equals(individual, that.individual);
    }

    @Override
    public int hashCode() {
        return Objects.hash(individual, crowdingDistance);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CDIndividual{");
        sb.append("individual=").append(individual);
        sb.append(", crowdingDistance=").append(crowdingDistance);
        sb.append('}');
        return sb.toString();
    }
}
