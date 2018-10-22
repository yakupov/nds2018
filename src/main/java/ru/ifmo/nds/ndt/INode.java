package ru.ifmo.nds.ndt;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public interface INode<T> extends INonDominationLevel<T> {
    NodeType getType();

    default TreeNode asTreeNode() {
        return null;
    }

    default LeafNode asLeafNode() {
        return null;
    }

    INode<T> insert(IIndividual<T> addend);

    List<IIndividual<T>> getDominatedMembers(IIndividual relativeTo, boolean remove);

    int size();

    @Override
    default MemberAdditionResult<T, INode<T>> addMembers(@Nonnull List<IIndividual<T>> addends) {
        final List<IIndividual<T>> evicted = new ArrayList<>(size());
        INode<T> workingNode = this;
        for (IIndividual<T> addend : addends) {
            evicted.addAll(workingNode.getDominatedMembers(addend, true));
            workingNode = workingNode.insert(addend);
        }
        return new MemberAdditionResult<>(evicted, workingNode);
    }

    @Override
    INode<T> copy();
}
