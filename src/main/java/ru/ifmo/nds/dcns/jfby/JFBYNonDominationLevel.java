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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
            boolean fullRecalc = false;
            for (int obj = 0; obj < objCount; ++obj) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (IIndividual addend : addends) {
                    min = Math.min(min, addend.getObjectives()[obj]);
                    max = Math.max(max, addend.getObjectives()[obj]);
                }
                final List<Double> sortedObj = sortedObjectives.get(obj);
                if (sortedObj.get(0) > min || sortedObj.get(sortedObj.size() - 1) < max) {
                    fullRecalc = true;
                    break;
                }
            }

            if (!fullRecalc) {
                int cdIndCtr = 0;
                int addendCtr = 0;
                final List<CDIndividual> evaluatedAddends = new ArrayList<>();
                for (IIndividual ind : modifiedLevel.getMembers()) {
                    if (addendCtr < addends.size() && ind == addends.get(addendCtr)) {
                        double cd = 0;
                        for (int obj = 0; obj < objCount; ++obj) {
                            final List<Double> sortedObj = sortedObjectives.get(obj);
                            final int idx = Collections.binarySearch(sortedObj, ind.getObjectives()[obj]);
                            if (idx < 0) {
                                try {
                                    cd += (sortedObj.get(-idx - 1) - sortedObj.get(-idx - 2)) /
                                            (sortedObj.get(sortedObj.size() - 1) - sortedObj.get(0));
                                } catch (Throwable t) {
                                    System.out.println(idx);
                                    System.out.println(sortedObj);
                                    System.out.println(ind.getObjectives()[obj]);
                                    throw t;
                                }
                            }
                        }
                        evaluatedAddends.add(new CDIndividual(ind, cd));
                        addendCtr++;
                    } else {
                        boolean added = false;
                        while (cdIndCtr < cdMembers.size() && !added) {
                            if (ind == cdMembers.get(cdIndCtr).getIndividual()) {
                                added = true;
                            }
                            cdIndCtr++;
                        }
                        if (!added) {
                            throw new RuntimeException("Impossible: \n" +
                                    ind + "\n" +
                                    addends + "\n" +
                                    cdMembers.stream().map(CDIndividual::getIndividual).collect(Collectors.toList()) + "\n" +
                                    modifiedLevel.getMembers());
                        }
                    }
                }

                cdIndCtr = 0;
                addendCtr = 0;
                final List<CDIndividual> newCDIndividuals = new ArrayList<>();
                while (newCDIndividuals.size() < modifiedLevel.getMembers().size()) {
                    for (IIndividual ind : modifiedLevel.getMembers()) {
                        if (addendCtr < addends.size() && ind == addends.get(addendCtr)) {
                            newCDIndividuals.add(evaluatedAddends.get(addendCtr));
                            addendCtr++;
                        } else {
                            boolean added = false;
                            while (cdIndCtr < cdMembers.size() && !added) {
                                if (ind == cdMembers.get(cdIndCtr).getIndividual()) {
                                    newCDIndividuals.add(cdMembers.get(cdIndCtr));
                                    added = true;
                                }
                                cdIndCtr++;
                            }
                        }
                    }
                }

                final List<IIndividual> addendsCopy = new ArrayList<>(addends);
                final List<List<Double>> newSortedObjectives = new ArrayList<>();
                for (int obj = 0; obj < objCount; ++obj) {
                    final List<Double> sortedObj = sortedObjectives.get(obj);
                    final List<Double> newList = new ArrayList<>();
                    addendsCopy.sort(new ObjectiveComparator(obj));
                    addendCtr = 0;
                    cdIndCtr = 0;
                    while (newList.size() < modifiedLevel.getMembers().size()) {
                        if (addendCtr >= addendsCopy.size()) {
                            newList.add(sortedObj.get(cdIndCtr));
                            ++cdIndCtr;
                        } else if (cdIndCtr >= sortedObj.size()) {
                            newList.add(addendsCopy.get(addendCtr).getObjectives()[obj]);
                            ++addendCtr;
                        } else if (sortedObj.get(cdIndCtr) <= addendsCopy.get(addendCtr).getObjectives()[obj]) {
                            newList.add(sortedObj.get(cdIndCtr));
                            ++cdIndCtr;
                        } else {
                            newList.add(addendsCopy.get(addendCtr).getObjectives()[obj]);
                            ++addendCtr;
                        }
                    }
                    newSortedObjectives.add(newList);
                }

                modifiedLevel.sortedObjectives = newSortedObjectives;
                modifiedLevel.cdMembers = newCDIndividuals;
            }
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
