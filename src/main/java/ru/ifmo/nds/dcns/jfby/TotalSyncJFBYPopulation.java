package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("unused")
public class TotalSyncJFBYPopulation extends JFBYPopulation {
    public TotalSyncJFBYPopulation(long expectedPopSize) {
        super(expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull List<INonDominationLevel> nonDominationLevels, long expectedPopSize) {
        super(nonDominationLevels, expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull JFB2014 sorter, long expectedPopSize) {
        super(sorter, expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull List<INonDominationLevel> nonDominationLevels, @Nonnull JFB2014 sorter, long expectedPopSize) {
        super(nonDominationLevels, sorter, expectedPopSize);
    }

    @Nonnull
    @Override
    public synchronized List<INonDominationLevel> getLevels() {
        return super.getLevels();
    }

    @Nullable
    @Override
    synchronized IIndividual intRemoveWorst() {
        return super.intRemoveWorst();
    }

    @Nonnull
    @Override
    public synchronized List<CDIndividual> getRandomSolutions(int count) {
        return super.getRandomSolutions(count);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized int addIndividual(@Nonnull IIndividual addend) {
        return super.addIndividual(addend);
    }

    @Override
    public synchronized JFBYPopulation clone() {
        return super.clone();
    }

    @Override
    public synchronized RankedPopulation<IIndividual> toRankedPopulation() {
        return super.toRankedPopulation();
    }
}
