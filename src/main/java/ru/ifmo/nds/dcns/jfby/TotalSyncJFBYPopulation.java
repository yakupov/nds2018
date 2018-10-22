package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("unused")
public class TotalSyncJFBYPopulation<T> extends JFBYPopulation<T> {
    public TotalSyncJFBYPopulation(long expectedPopSize) {
        super(expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull List<JFBYNonDominationLevel<T>> nonDominationLevels, long expectedPopSize) {
        super(nonDominationLevels, expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull JFB2014 sorter, long expectedPopSize) {
        super(sorter, expectedPopSize);
    }

    public TotalSyncJFBYPopulation(@Nonnull List<JFBYNonDominationLevel<T>> nonDominationLevels, @Nonnull JFB2014 sorter, long expectedPopSize) {
        super(nonDominationLevels, sorter, expectedPopSize);
    }

    @Nonnull
    @Override
    public synchronized PopulationSnapshot<T> getSnapshot() {
        return super.getSnapshot();
    }

    @Nullable
    @Override
    synchronized IIndividual<T> intRemoveWorst() {
        return super.intRemoveWorst();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized int addIndividual(@Nonnull IIndividual<T> addend) {
        return super.addIndividual(addend);
    }

    @Override
    public synchronized JFBYPopulation<T> clone() {
        return super.clone();
    }

    @Override
    public synchronized RankedPopulation<IIndividual<T>> toRankedPopulation() {
        return super.toRankedPopulation();
    }
}
