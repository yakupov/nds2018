package ru.ifmo.nds.ndt;

import ru.ifmo.nds.AbstractNonDominationLevel;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.util.QuickSelect;

import javax.annotation.Nonnull;
import java.util.*;

public class LeafNode extends AbstractNonDominationLevel implements INode {
    @Nonnull
    private List<IIndividual> content; //TODO: treeset or sorted array for faster addition and search

    @Nonnull
    private final Comparator<IIndividual> dominationComparator;

    @Nonnull
    private final INdtSettings ndtSettings;

    private final int dimension; //Next TreeNode's suggested split

    private final QuickSelect quickSelect;

    @SuppressWarnings("WeakerAccess")
    public LeafNode(@Nonnull List<IIndividual> content,
                    @Nonnull Comparator<IIndividual> dominationComparator,
                    @Nonnull INdtSettings ndtSettings,
                    int dimension) {
        super(content);
        this.content = content;
        this.dominationComparator = dominationComparator;
        this.ndtSettings = ndtSettings;
        this.dimension = dimension;

        this.quickSelect = ndtSettings.getQuickSelect();
    }

    @Override
    public NodeType getType() {
        return NodeType.LEAF;
    }

    @Override
    public LeafNode asLeafNode() {
        return this;
    }

    @Override
    public INode insert(IIndividual addend) {
        if (content.size() < ndtSettings.getBucketCapacity()) {
            content.add(addend);
            return this;
        } else {
            final double[] kThObjectives = new double[content.size()];
            for (int i = 0; i < content.size(); ++i) {
                kThObjectives[i] = content.get(i).getObjectives()[dimension];
            }
            final double split = quickSelect.getMedian(kThObjectives);
            final List<IIndividual> l = new ArrayList<>();
            final List<IIndividual> r = new ArrayList<>();
            for (IIndividual individual : content) {
                if (individual.getObjectives()[dimension] <= split) {
                    l.add(individual);
                } else {
                    r.add(individual);
                }
            }
            if (addend.getObjectives()[dimension] <= split) {
                l.add(addend);
            } else {
                r.add(addend);
            }
            final int nextDimension = (dimension + 1) % ndtSettings.getDimensionsCount();
            final INode lNode = new LeafNode(l, dominationComparator, ndtSettings, nextDimension);
            final INode rNode = new LeafNode(r, dominationComparator, ndtSettings, nextDimension);
            return new TreeNode(lNode, rNode, dimension, split);
        }
    }

    @Override
    public List<IIndividual> getDominatedMembers(IIndividual relativeTo, boolean remove) {
        final List<IIndividual> rs = new ArrayList<>(content.size());
        final List<IIndividual> remains = new ArrayList<>(content.size());
        for (IIndividual individual : content) {
            if (dominationComparator.compare(relativeTo, individual) < 0) {
                rs.add(individual);
            } else {
                remains.add(individual);
            }
        }
        if (remove) {
            content = remains;
        }
        return rs;
    }

    @Override
    public int size() {
        return content.size();
    }

    @Override
    public List<IIndividual> getMembers() {
        return Collections.unmodifiableList(content);
    }


    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point) {
        for (IIndividual individual : content) {
            if (dominationComparator.compare(point, individual) > 0) {
                //System.out.println("Dominated by " + individual);
                return true;
            }
        }
        return false;
    }

    @Override
    public INode copy() {
        final List<IIndividual> newContent = new ArrayList<>(content.size());
        newContent.addAll(content);
        return new LeafNode(newContent, dominationComparator, ndtSettings, dimension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeafNode node = (LeafNode) o;
        return dimension == node.dimension &&
                Objects.equals(content, node.content) &&
                Objects.equals(dominationComparator, node.dominationComparator) &&
                Objects.equals(ndtSettings, node.ndtSettings) &&
                Objects.equals(quickSelect, node.quickSelect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, dominationComparator, ndtSettings, dimension, quickSelect);
    }

    @Override
    public String toString() {
        return "LeafNode{" + "content=" + content +
                ", dimension=" + dimension +
                '}';
    }
}
