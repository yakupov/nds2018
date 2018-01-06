package ru.ifmo.nds.dcns.lppsn;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.PPSN2014;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ru.itmo.nds.util.ComparisonUtils.dominates;

public class LPPSNNonDominationLevel implements INonDominationLevel {
    @Nonnull
    private final PPSN2014 sorter;

    @Nonnull
    private List<IIndividual> members;

    LPPSNNonDominationLevel(PPSN2014 sorter) {
        this(sorter, new ArrayList<>());
    }

    private LPPSNNonDominationLevel(@Nonnull PPSN2014 sorter, @Nonnull List<IIndividual> members) {
        this.sorter = sorter;
        this.members = members;
    }

    @Override
    @Nonnull
    public List<IIndividual> getMembers() {
        return members;
    }

    @Override
    public ArrayList<IIndividual> addMembers(@Nonnull List<IIndividual> addends) {
        final int[] ranks = new int[members.size()];
        final RankedPopulation<IIndividual> rp = sorter.addRankedMembers(members, ranks, addends, 0);
        final ArrayList<IIndividual> currLevel = new ArrayList<>(ranks.length + addends.size());
        final ArrayList<IIndividual> nextLevel = new ArrayList<>(ranks.length);
        for (int i = 0; i < rp.getPop().length; ++i) {
            if (rp.getRanks()[i] == 0)
                currLevel.add(rp.getPop()[i]);
            else
                nextLevel.add(rp.getPop()[i]);
        }
        members = currLevel;
        return nextLevel;
    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point) {
        final double[] pointObj = point.getObjectives();
        for (IIndividual member: members) {
            final double[] memberObj = member.getObjectives();
            if (memberObj[0] > pointObj[0])
                break;
            if (dominates(memberObj, pointObj, pointObj.length) < 0)
                return true;
        }
        return false;
    }

    @Override
    public LPPSNNonDominationLevel copy() {
        final List<IIndividual> newMembers = new ArrayList<>(members.size());
        newMembers.addAll(members);
        return new LPPSNNonDominationLevel(sorter, newMembers);
    }

    @Override
    public String toString() {
        return "members=" + members.stream()
                .map(IIndividual::getObjectives)
                .map(Arrays::toString)
                .collect(Collectors.toList());
    }
}
