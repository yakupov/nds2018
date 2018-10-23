package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.util.CrowdingDistanceData;
import ru.ifmo.nds.util.Utils;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.itmo.nds.util.ComparisonUtils.dominates;

@ThreadSafe
@Immutable
public class JFBYNonDominationLevel<T> implements INonDominationLevel<T> {
    @Nonnull
    private final JFB2014 sorter;

    @Nonnull
    private final List<IIndividual<T>> members;

    @Nonnull
    private final SortedObjectives<T> sortedObjectives;

    /**
     * Inefficient (O(NlogN) CD recalc) new level construction
     * @param sorter Sorter impl
     * @param members Level members
     */
    public JFBYNonDominationLevel(@Nonnull JFB2014 sorter,
                                  @Nonnull List<IIndividual<T>> members) {
        this.sorter = sorter;
        if (!members.isEmpty()) {
            final CrowdingDistanceData<T> cdd = SortedObjectives.calculateCrowdingDistances(members.get(0).getObjectives().length, members);
            this.members = cdd.getIndividuals();
            this.sortedObjectives = cdd.getSortedObjectives();
        } else {
            this.members = Collections.emptyList();
            this.sortedObjectives = SortedObjectives.empty(0);
        }
    }

    public JFBYNonDominationLevel(@Nonnull JFB2014 sorter,
                                  @Nonnull List<IIndividual<T>> members,
                                  @Nonnull SortedObjectives<T> sortedObjectives) {
//        for (List<IIndividual<T>> so : sortedObjectives) {
//            assert so != null;
//            assert so.size() == members.size();
//        }
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
    public SortedObjectives<T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public MemberAdditionResult<T, JFBYNonDominationLevel<T>> addMembers(@Nonnull List<IIndividual<T>> addends) {
//        for (List<IIndividual<T>> ls : sortedObjectives) {
//            assert ls.size() == members.size();
//        }

        final int[] ranks = new int[members.size()];
        final RankedPopulation<IIndividual<T>> rp = sorter.addRankedMembers(members, ranks, addends, 0);
        final ArrayList<IIndividual<T>> currLevel = new ArrayList<>(ranks.length + addends.size());
        final ArrayList<IIndividual<T>> nextLevel = new ArrayList<>(ranks.length);
        final Set<IIndividual<T>> nextLevelSet = new HashSet<>(ranks.length);

        for (int i = 0; i < rp.getPop().length; ++i) {
            if (rp.getRanks()[i] == 0) {
                currLevel.add(rp.getPop()[i]);
            } else {
                nextLevel.add(rp.getPop()[i]);
                nextLevelSet.add(rp.getPop()[i]);
            }
        }

        //assert nextLevel.size() == new HashSet<>(nextLevel).size();
        //assert currLevel.size() == new HashSet<>(currLevel).size();

        final CrowdingDistanceData<T> cdd = Utils.recalcCrowdingDistances(
                addends.get(0).getObjectives().length,
                sortedObjectives,
                addends,
                nextLevelSet,
                currLevel
        );

//        try {
//            assert cdd.getIndividuals().size() == currLevel.size();
//
//            for (List<IIndividual<T>> iIndividuals : cdd.getSortedObjectives()) {
//                assert iIndividuals.size() == cdd.getIndividuals().size();
//
//                if (iIndividuals.size() != currLevel.size()) {
//                    System.err.println(iIndividuals);
//                    System.err.println(currLevel);
//                    throw new AssertionError("Ass failed");
//                }
//            }
//        } catch (Throwable t) {
//            System.err.println(cdd.getIndividuals());
//            System.err.println(cdd.getSortedObjectives());
//            System.err.println(addends);
//            System.err.println(members);
//            System.err.println(sortedObjectives);
//            System.err.println(currLevel);
//            throw t;
//        }

        return new MemberAdditionResult<>(
                nextLevel,
                new JFBYNonDominationLevel<>(sorter, cdd.getIndividuals(), cdd.getSortedObjectives())
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
