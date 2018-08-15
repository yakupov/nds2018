package ru.ifmo.nds.ndt;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.INonDominationLevel;
import ru.ifmo.nds.PopulationSnapshot;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static ru.itmo.nds.util.ComparisonUtils.dominates;

public class NdtManagedPopulation implements IManagedPopulation {
    private final List<INode> ndLayers = new ArrayList<>();
    private int size = 0;

    private final Comparator<IIndividual> dominationComparator;
    private final INdtSettings ndtSettings;

    public NdtManagedPopulation(INdtSettings ndtSettings) {
        this(
                (o1, o2) -> dominates(o1.getObjectives(), o2.getObjectives(), ndtSettings.getDimensionsCount()),
                ndtSettings
        );
    }

    @SuppressWarnings("WeakerAccess")
    public NdtManagedPopulation(Comparator<IIndividual> dominationComparator, INdtSettings ndtSettings) {
        this.dominationComparator = dominationComparator;
        this.ndtSettings = ndtSettings;
    }

    @Override
    public int addIndividual(@Nonnull IIndividual addend) {
        final int rank = determineRank(addend);
        //System.out.println("Addend " + addend + " rank: " + rank);
        if (rank >= ndLayers.size()) {
            final List<IIndividual> individuals = new ArrayList<>();
            individuals.add(addend);
            final LeafNode node = new LeafNode(individuals, dominationComparator, ndtSettings, 0);
            ndLayers.add(node);
        } else {
            List<IIndividual> addends = Collections.singletonList(addend);
            int i = rank;
            while (!addends.isEmpty() && i < ndLayers.size()) {
                final INode level = ndLayers.get(i);
                final List<IIndividual> nextAddends = new ArrayList<>();
                for (IIndividual individual : addends) {
                    final List<IIndividual> dominated = level.getDominatedMembers(individual, true);
                    level.getDominatedMembers(individual, true);
                    ndLayers.set(i, level.insert(individual));
                    nextAddends.addAll(dominated);
                }
                addends = nextAddends;
                i++;
            }
            if (!addends.isEmpty()) {
                final LeafNode node = new LeafNode(addends, dominationComparator, ndtSettings, 0);
                ndLayers.add(node);
            }
        }

        ++size;
        return rank;
    }

    @Nonnull
    @Override
    public PopulationSnapshot getSnapshot() {
        @SuppressWarnings("unchecked")
        final List<INonDominationLevel> levels = (List<INonDominationLevel>) ((List) ndLayers);
        return new PopulationSnapshot(levels, size);
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * @return A copy of this population. All layers are also copied.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public IManagedPopulation clone() {
        final NdtManagedPopulation copy = new NdtManagedPopulation(dominationComparator, ndtSettings);
        for (INode layer : ndLayers) {
            copy.ndLayers.add(layer.copy());
        }
        copy.size = size;
        return copy;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NdtManagedPopulation{");
        sb.append("ndLayers=").append(ndLayers);
        sb.append(", size=").append(size);
        sb.append(", dominationComparator=").append(dominationComparator);
        sb.append(", ndtSettings=").append(ndtSettings);
        sb.append('}');
        return sb.toString();
    }
}
