package ru.ifmo.nds.dcns.enlu;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;
import ru.ifmo.nds.util.SortedObjectives;
import ru.ifmo.nds.util.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static ru.ifmo.nds.util.Utils.getWorstCDIndividual;

public class ENLUManagedPopulation<T> implements IManagedPopulation<T> {
    private final Set<IIndividual<T>> individuals;
    private final List<ENLUNonDominationLevel<T>> ranks;
    private final long maxSize;

    public ENLUManagedPopulation(long maxSize) {
        this(new HashSet<>(), new ArrayList<>(), maxSize);
    }

    public ENLUManagedPopulation(Set<IIndividual<T>> individuals, List<ENLUNonDominationLevel<T>> ranks, long maxSize) {
        this.individuals = individuals;
        this.ranks = ranks;
        this.maxSize = maxSize;
    }

    @Override
    public synchronized int addIndividual(@Nonnull IIndividual<T> nInd) {
        final int rg = doAddIndividual(nInd);
        if (individuals.size() > maxSize) {
            intRemoveWorst();
        }
        return rg;
    }


    private static <T> ENLUNonDominationLevel<T> removeIndividualFromLevel(@Nonnull final ENLUNonDominationLevel<T> lastLevel,
                                                                          @Nonnull final IIndividual<T> removedIndividual) {
        final SortedObjectives<IIndividual<T>, T> nso = lastLevel.getSortedObjectives().update(
                Collections.emptyList(),
                Collections.singletonList(removedIndividual),
                (i, d) -> new FitnessAndCdIndividual<>(i.getObjectives(), d, i.getPayload())
        );

        return new ENLUNonDominationLevel<>(new HashSet<>(nso.getLexSortedPop()), nso);
    }

    @Nullable
    IIndividual<T> intRemoveWorst() {
        final int lastLevelIndex = ranks.size() - 1;
        final ENLUNonDominationLevel<T> lastLevel = ranks.get(lastLevelIndex);
        if (lastLevel.getMembers().size() <= 1) {
            ranks.remove(lastLevelIndex);
            if (lastLevel.getMembers().isEmpty()) {
                System.err.println("Empty last ND level! Levels = " + ranks);
                return null;
            } else {
                final IIndividual<T> individual = lastLevel.getMembers().get(0);
                individuals.remove(individual);
                return individual;
            }
        } else {
            final IIndividual<T> removedIndividual = getWorstCDIndividual(lastLevel);
            if (removedIndividual == null) {
                return null;
            }
            final ENLUNonDominationLevel<T> newLevel = removeIndividualFromLevel(lastLevel, removedIndividual);
            ranks.set(lastLevelIndex, newLevel);
            individuals.remove(removedIndividual);
            return removedIndividual;
        }
    }

    private int doAddIndividual(@Nonnull IIndividual<T> nInd) {
        if (individuals.contains(nInd)) {
            return detRankOfExPoint(nInd);
        } else {
            individuals.add(nInd);
        }

        for (int i = 0; i < ranks.size(); ++i) {
            boolean dominates, dominated, nd;
            dominates = dominated = nd = false;

            for (IIndividual<T> ind: ranks.get(i).getMembers()) {
                int domComparisonResult = Utils.dominates(nInd.getObjectives(), ind.getObjectives(), nInd.getObjectives().length);
                //nInd.compareDom(ind);
                if (domComparisonResult == 0)
                    nd = true;
                else if (domComparisonResult > 0) {
                    dominated = true;
                    break;
                } else {
                    dominates = true;
                }
            }

            if (dominated)
                //noinspection UnnecessaryContinue
                continue;
            else if (!nd && dominates) {
                final Set<IIndividual<T>> newRank = new HashSet<>();
                newRank.add(nInd);
                ranks.add(i, new ENLUNonDominationLevel<>(newRank, SortedObjectives.create(
                        nInd.getObjectives().length,
                        Collections.singletonList(nInd),
                        (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
                )));
                return i;
            } else { //enlu update procedure
                List<IIndividual<T>> dominatedList = Collections.singletonList(nInd);
                final int rs = i;
                while (!dominatedList.isEmpty()) {
                    if (i >= ranks.size()) {
                        ranks.add(i, new ENLUNonDominationLevel<>(new HashSet<>(dominatedList), SortedObjectives.create(
                                nInd.getObjectives().length,
                                dominatedList,
                                (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
                        )));
                        return rs;
                    } else {
                        final INonDominationLevel.MemberAdditionResult<T, ENLUNonDominationLevel<T>> mar =
                                ranks.get(i).addMembers(dominatedList);
                        dominatedList = mar.getEvictedMembers();
                        ranks.set(i, mar.getModifiedLevel());
                        ++i;
                    }
                }
                return rs;
            }
        }

        //Dominated by everyone
        ranks.add(new ENLUNonDominationLevel<>(Collections.singleton(nInd), SortedObjectives.create(
                nInd.getObjectives().length,
                Collections.singletonList(nInd),
                (ind, d) -> new FitnessAndCdIndividual<>(ind.getObjectives(), d, ind.getPayload())
        )));
        return ranks.size() - 1;
    }

    private int detRankOfExPoint(IIndividual<T> ind) {
        for (int i = 0; i < ranks.size(); ++i) {
            if (ranks.get(i).getMembersSet().contains(ind))
                return i;
        }
        throw new RuntimeException("Point not exists");
    }

    @Nonnull
    @Override
    public synchronized PopulationSnapshot<T> getSnapshot() {
        final List<INonDominationLevel<T>> levelsSnap = new ArrayList<>();
        int size = 0;
        for (ENLUNonDominationLevel<T> rank : ranks) {
            size += rank.getMembersSet().size();
            levelsSnap.add(rank.copy());
        }
        return new PopulationSnapshot<>(levelsSnap, size);
    }

    @Override
    public int size() {
        return individuals.size();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public synchronized IManagedPopulation<T> clone() {
        final List<ENLUNonDominationLevel<T>> ranksSnap = new ArrayList<>();
        for (ENLUNonDominationLevel<T> rank : ranks) {
            ranksSnap.add(rank.copy());
        }
        return new ENLUManagedPopulation<>(new HashSet<>(individuals), ranksSnap, maxSize);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ENLUManagedPopulation{");
        sb.append("individuals=").append(individuals);
        sb.append(", ranks=").append(ranks);
        sb.append(", maxSize=").append(maxSize);
        sb.append('}');
        return sb.toString();
    }
}
