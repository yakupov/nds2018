package ru.ifmo.nds.impl;

import ru.ifmo.nds.IIndividual;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;

@ThreadSafe
@Immutable
public class CDIndividualWithRank extends CDIndividual {
    private final int rank;

    public CDIndividualWithRank(@Nonnull IIndividual individual, double crowdingDistance, int rank) {
        super(individual, crowdingDistance);
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CDIndividualWithRank that = (CDIndividualWithRank) o;
        return rank == that.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rank);
    }

    @Override
    public String toString() {
        return "CDIndividualWithRank{" +
                "rank=" + rank +
                "} " + super.toString();
    }
}
