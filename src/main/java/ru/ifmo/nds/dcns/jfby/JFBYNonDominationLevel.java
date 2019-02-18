package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;
import ru.ifmo.nds.util.SortedObjectives;
import ru.ifmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static ru.ifmo.nds.util.Utils.dominates;

@ThreadSafe
@Immutable
public class JFBYNonDominationLevel<T> implements INonDominationLevel<T> {
    @Nonnull
    private final JFB2014 sorter;

    @Nonnull
    private final List<IIndividual<T>> members;

    @Nonnull
    private final SortedObjectives<IIndividual<T>, T> sortedObjectives;

    /**
     * Inefficient (O(NlogN) CD recalc) new level construction
     * @param sorter Sorter impl
     * @param members Level members
     */
    public JFBYNonDominationLevel(@Nonnull JFB2014 sorter,
                                  @Nonnull List<IIndividual<T>> members) {
        this.sorter = sorter;
        if (!members.isEmpty()) {
            final int dim = members.get(0).getObjectives().length;
            this.sortedObjectives = SortedObjectives.create(dim, members, (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload()));
            this.members = sortedObjectives.getLexSortedPop();
        } else {
            this.members = Collections.emptyList();
            this.sortedObjectives = SortedObjectives.empty(0);
        }
    }

    public JFBYNonDominationLevel(@Nonnull JFB2014 sorter,
                                  @Nonnull List<IIndividual<T>> members,
                                  @Nonnull SortedObjectives<IIndividual<T>, T> sortedObjectives) {
        this.sorter = sorter;
        this.members = Collections.unmodifiableList(members);
        this.sortedObjectives = sortedObjectives;
    }

    @Override
    @Nonnull
    public List<IIndividual<T>> getMembers() {
        return members;
    }

    @Nonnull
    public SortedObjectives<IIndividual<T>, T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public MemberAdditionResult<T, JFBYNonDominationLevel<T>> addMembers(@Nonnull List<IIndividual<T>> addends) {
        final int[] ranks = new int[members.size()];
        final RankedPopulation<IIndividual<T>> rp = sorter.addRankedMembers(members, ranks, addends, 0);
        final ArrayList<IIndividual<T>> nextLevel = new ArrayList<>(ranks.length);

        for (int i = 0; i < rp.getPop().length; ++i) {
            if (rp.getRanks()[i] != 0) {
                nextLevel.add(rp.getPop()[i]);
            }
        }

        final SortedObjectives<IIndividual<T>, T> nso = sortedObjectives.update(
                addends,
                nextLevel,
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );

        return new MemberAdditionResult<>(
                nextLevel,
                new JFBYNonDominationLevel<>(sorter, nso.getLexSortedPop(), nso)
        );
    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point) {
        final double[] pointObj = point.getObjectives();
        for (IIndividual member : members) {
            final double[] memberObj = member.getObjectives();
            if (memberObj[0] > pointObj[0])
                break;
            if (dominates(memberObj, pointObj, pointObj.length) < 0)
                return true;
        }
        return false;
    }

    @Override
    public JFBYNonDominationLevel<T> copy() {
        final List<IIndividual<T>> newMembers = new ArrayList<>(members.size());
        newMembers.addAll(members);
        return new JFBYNonDominationLevel<>(sorter, newMembers, sortedObjectives);
    }

    @Override
    public String toString() {
        return "members=" + members.stream()
                .map(IIndividual::getObjectives)
                .map(Arrays::toString)
                .collect(Collectors.toList());
    }
}
