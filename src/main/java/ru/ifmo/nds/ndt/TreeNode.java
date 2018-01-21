package ru.ifmo.nds.ndt;

import ru.ifmo.nds.IIndividual;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TreeNode implements INode {
    @Nonnull
    private INode left;

    @Nonnull
    private INode right;

    private final int dimension;
    private final double split;

    private int size;

    public TreeNode(@Nonnull INode left,
                    @Nonnull INode right,
                    int dimension,
                    double split) {
        this.left = left;
        this.right = right;
        this.dimension = dimension;
        this.split = split;

        this.size = left.size() + right.size();
    }

    @Override
    public NodeType getType() {
        return NodeType.TREE;
    }

    @Override
    public TreeNode asTreeNode() {
        return this;
    }

    @Override
    public INode insert(IIndividual addend) {
        if (addend.getObjectives()[dimension] <= split) {
            this.left = left.insert(addend);
        } else {
            this.right = right.insert(addend);
        }
        ++size;
        return this;
    }

    @Override
    public List<IIndividual> getDominatedMembers(IIndividual relativeTo, boolean remove) {
        final List<IIndividual> rs;
        if (relativeTo.getObjectives()[dimension] <= split) {
            rs = new ArrayList<>();
            rs.addAll(left.getDominatedMembers(relativeTo, remove));
            rs.addAll(right.getDominatedMembers(relativeTo, remove));
        } else {
            rs = right.getDominatedMembers(relativeTo, remove);
        }
        if (remove) {
            size -= rs.size();
        }
        return rs;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public List<IIndividual> getMembers() {
        final List<IIndividual> lMembers = left.getMembers();
        final List<IIndividual> rMembers = right.getMembers();
        final List<IIndividual> rs = new ArrayList<>(lMembers.size() + rMembers.size());
        rs.addAll(lMembers);
        rs.addAll(rMembers);
        return rs;
    }

    @Override
    public boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point) {
        return left.dominatedByAnyPointOfThisLayer(point) || right.dominatedByAnyPointOfThisLayer(point);
    }

    @Override
    public INode copy() {
        return new TreeNode(left.copy(), right.copy(), dimension, split);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreeNode treeNode = (TreeNode) o;
        return dimension == treeNode.dimension &&
                Double.compare(treeNode.split, split) == 0 &&
                size == treeNode.size &&
                Objects.equals(left, treeNode.left) &&
                Objects.equals(right, treeNode.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, dimension, split, size);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TreeNode{");
        sb.append("left=").append(left);
        sb.append(", right=").append(right);
        sb.append(", dimension=").append(dimension);
        sb.append(", split=").append(split);
        sb.append(", size=").append(size);
        sb.append('}');
        return sb.toString();
    }
}
