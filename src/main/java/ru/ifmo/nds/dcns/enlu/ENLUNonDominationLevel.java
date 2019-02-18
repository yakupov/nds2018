package ru.ifmo.nds.dcns.enlu;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;
import ru.ifmo.nds.util.AscLexSortComparator;
import ru.ifmo.nds.util.SortedObjectives;
import ru.ifmo.nds.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;

import static ru.ifmo.nds.util.Utils.dominates;

public class ENLUNonDominationLevel<T> implements INonDominationLevel<T> {
    @Nonnull
    private final Set<IIndividual<T>> members;

    @Nonnull
    private final SortedObjectives<IIndividual<T>, T> sortedObjectives;

    public ENLUNonDominationLevel(@Nonnull Set<IIndividual<T>> members,
                                  @Nonnull SortedObjectives<IIndividual<T>, T> sortedObjectives) {
        this.members = members;
        this.sortedObjectives = sortedObjectives;
    }

    @Override
    public List<IIndividual<T>> getMembers() {
        return sortedObjectives.getLexSortedPop();
    }

    public Set<IIndividual<T>> getMembersSet() {
        return members;
    }

    @Nonnull
    public SortedObjectives<IIndividual<T>, T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public MemberAdditionResult<T, ENLUNonDominationLevel<T>> addMembers(@Nonnull List<IIndividual<T>> addends) {
        final Set<IIndividual<T>> nextAddends = new HashSet<>();
        final Set<IIndividual<T>> newMembers = new HashSet<>(members);
        for (IIndividual<T> addend : addends) {
            final Set<IIndividual<T>> lastDom = new HashSet<>();
            for (IIndividual<T> oldTenant : newMembers) {
                if (Utils.dominates(addend.getObjectives(), oldTenant.getObjectives(), addend.getObjectives().length) < 0) {
                    nextAddends.add(oldTenant);
                    lastDom.add(oldTenant);
                }
            }
            newMembers.removeAll(lastDom);
        }
        newMembers.addAll(addends);

        final List<IIndividual<T>> nextAddendList = new ArrayList<>(nextAddends);
        nextAddendList.sort(AscLexSortComparator.getInstance());
        final SortedObjectives<IIndividual<T>, T> nso = sortedObjectives.update(
                addends,
                nextAddendList,
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );
        return new MemberAdditionResult<>(new ArrayList<>(nextAddendList), new ENLUNonDominationLevel<>(newMembers, nso));

    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual<T> point) {
        for (IIndividual<T> member : members) {
            if (dominates(member.getObjectives(), point.getObjectives(), point.getObjectives().length) < 0)
                return true;
        }
        return false;
    }

    @Override
    public ENLUNonDominationLevel<T> copy() {
        return new ENLUNonDominationLevel<>(new HashSet<>(members), sortedObjectives);
    }
}
