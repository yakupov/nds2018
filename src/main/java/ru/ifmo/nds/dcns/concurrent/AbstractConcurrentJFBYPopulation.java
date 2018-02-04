package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;

import javax.annotation.Nullable;

abstract class AbstractConcurrentJFBYPopulation implements IManagedPopulation {
    @Nullable
    abstract IIndividual intRemoveWorst();

    @Override
    abstract public AbstractConcurrentJFBYPopulation clone();
}
