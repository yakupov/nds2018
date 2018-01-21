package ru.ifmo.nds.ndt;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.INonDominationLevel;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public interface INode extends INonDominationLevel {
    NodeType getType();

    default TreeNode asTreeNode() {
        return null;
    }

    default LeafNode asLeafNode() {
        return null;
    }

    INode insert(IIndividual addend);

    List<IIndividual> getDominatedMembers(IIndividual relativeTo, boolean remove);

    int size();

    @Override
    default MemberAdditionResult addMembers(@Nonnull List<IIndividual> addends) {
        final List<IIndividual> evicted = new ArrayList<>(size());
        INode workingNode = this;
        for (IIndividual addend : addends) {
            evicted.addAll(workingNode.getDominatedMembers(addend, true));
            workingNode = workingNode.insert(addend);
        }
        return new MemberAdditionResult(evicted, workingNode);
    }

    @Override
    INode copy();
}
