package ru.ifmo.nds.ndt;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.impl.CDIndividualWithRank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

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

    private int determineRank(IIndividual point) {
        int l = 0;
        int r = ndLayers.size() - 1;
        int lastNonDominating = r + 1;
        while (l <= r) {
            final int test = (l + r) / 2;
            if (!ndLayers.get(test).dominatedByAnyPointOfThisLayer(point)) {
                lastNonDominating = test;
                r = test - 1;
            } else {
                l = test + 1;
            }
        }

        return lastNonDominating;
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
    public List<INode> getLevels() {
        return ndLayers;
    }

    @Nullable
    @Override
    public IIndividual removeWorst() {
        throw new RuntimeException("Not supported yet");
    }

    @Nonnull
    @Override
    public List<CDIndividualWithRank> getRandomSolutions(int count) {
        throw new RuntimeException("Not supported yet");
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
