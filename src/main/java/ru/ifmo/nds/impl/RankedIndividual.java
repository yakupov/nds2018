package ru.ifmo.nds.impl;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Objects;

@ThreadSafe
@Immutable
public class RankedIndividual<T> extends FitnessAndCdIndividual<T> {
    private final int rank;

    public RankedIndividual(double[] objectives, double crowdingDistance, int rank, T payload) {
        super(objectives, crowdingDistance, payload);
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return "RankedIndividual{" +
                "rank=" + rank +
                "} " + super.toString();
    }
}
