package ru.ifmo.nds.dcns.jfby;

import ru.ifmo.nds.AbstractNonDominationLevel;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.dcns.sorter.JFB2014;
import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.ObjectiveComparator;
import ru.itmo.nds.util.RankedPopulation;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.stream.Collectors;

import static ru.itmo.nds.util.ComparisonUtils.dominates;

@ThreadSafe
public class JFBYNonDominationLevel extends AbstractNonDominationLevel implements INonDominationLevel {
    @Nonnull
    private final JFB2014 sorter;

    @Nonnull
    private final List<IIndividual> members;

    public JFBYNonDominationLevel(@Nonnull JFB2014 sorter, @Nonnull List<IIndividual> members) {
        this.sorter = sorter;
        this.members = Collections.unmodifiableList(members);
    }

    @Override
    @Nonnull
    public List<IIndividual> getMembers() {
        return members;
    }

    @Override
    public MemberAdditionResult addMembers(@Nonnull List<IIndividual> addends) {
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
        final JFBYNonDominationLevel modifiedLevel = new JFBYNonDominationLevel(sorter, currLevel);

        if (this.sortedObjectives != null && this.sortedObjectives.size() > 3) {
            final int objCount = addends.get(0).getObjectives().length;

            final List<Double> mins = new ArrayList<>();
            final List<Double> maxs = new ArrayList<>();
            for (int obj = 0; obj < objCount; ++obj) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (IIndividual addend : addends) {
                    min = Math.min(min, addend.getObjectives()[obj]);
                    max = Math.max(max, addend.getObjectives()[obj]);
                }
                mins.add(min);
                maxs.add(max);
            }

            final Set<IIndividual> removed = new HashSet<>(nextLevel);
            final Map<IIndividual, Double> cdMap = new HashMap<>();
            final List<List<IIndividual>> newSortedObjectives = new ArrayList<>();
            for (int obj = 0; obj < objCount; ++obj) {
                final List<IIndividual> sortedAddends = new ArrayList<>(addends);
                final ObjectiveComparator comparator = new ObjectiveComparator(obj);
                sortedAddends.sort(comparator);

                final List<IIndividual> newSortedObjective = new ArrayList<>();
                final List<IIndividual> oldSortedObjective = sortedObjectives.get(obj);
                int cAddends = 0;
                int cOldSorted = 0;
                while (newSortedObjective.size() < modifiedLevel.getMembers().size()) {
                    if (cOldSorted >= oldSortedObjective.size()) {
                        newSortedObjective.add(sortedAddends.get(cAddends++));
                    } else if (cAddends >= sortedAddends.size()) {
                        final IIndividual individual = oldSortedObjective.get(cOldSorted);
                        if (!removed.contains(individual)) {
                            newSortedObjective.add(individual);
                        }
                        ++cOldSorted;
                    } else if (comparator.compare(sortedAddends.get(cAddends), oldSortedObjective.get(cOldSorted)) <= 0) {
                        newSortedObjective.add(sortedAddends.get(cAddends++));
                    } else {
                        final IIndividual individual = oldSortedObjective.get(cOldSorted);
                        if (!removed.contains(individual)) {
                            newSortedObjective.add(individual);
                        }
                        ++cOldSorted;
                    }
                }
                newSortedObjectives.add(newSortedObjective);

                cdMap.put(newSortedObjective.get(0), Double.POSITIVE_INFINITY);
                cdMap.put(newSortedObjective.get(newSortedObjective.size() - 1), Double.POSITIVE_INFINITY);
                for (int j = 1; j < newSortedObjective.size() - 1; j++) {
                    double distance = cdMap.getOrDefault(newSortedObjective.get(j), 0.0);
                    distance += (newSortedObjective.get(j + 1).getObjectives()[obj] -
                            newSortedObjective.get(j - 1).getObjectives()[obj])
                            / (maxs.get(obj) - mins.get(obj));
                    cdMap.put(newSortedObjective.get(j), distance);
                }
            }

            final List<CDIndividual> rs = new ArrayList<>();
            for (IIndividual member : members) {
                rs.add(new CDIndividual(member, cdMap.get(member)));
            }

            modifiedLevel.cdMembers = rs;
            modifiedLevel.sortedObjectives = newSortedObjectives;
        } else if (modifiedLevel.getMembers().size() < 10) { //Explicitly calculate CD for small levels. Hope for lesser lock contention
            modifiedLevel.getMembersWithCD();
        }

        return new MemberAdditionResult(nextLevel, modifiedLevel);
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
    public JFBYNonDominationLevel copy() {
        final List<IIndividual> newMembers = new ArrayList<>(members.size());
        newMembers.addAll(members);
        return new JFBYNonDominationLevel(sorter, newMembers);
    }

    @Override
    public String toString() {
        return "members=" + members.stream()
                .map(IIndividual::getObjectives)
                .map(Arrays::toString)
                .collect(Collectors.toList());
    }
}
