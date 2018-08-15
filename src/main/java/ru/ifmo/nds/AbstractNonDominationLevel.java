package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.Utils;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

import static ru.ifmo.nds.util.Utils.calculateCrowdingDistances;

public abstract class AbstractNonDominationLevel implements INonDominationLevel {
    @Nonnull
    private final List<CDIndividual> cdMembers;

    @Nonnull
    protected final List<List<IIndividual>> sortedObjectives;

    public AbstractNonDominationLevel(@Nonnull final List<CDIndividual> cdMembers,
                                      @Nonnull final List<List<IIndividual>> sortedObjectives) {
        this.cdMembers = cdMembers;
        this.sortedObjectives = sortedObjectives;
    }

    public AbstractNonDominationLevel(@Nonnull List<IIndividual> members) {
        if (!members.isEmpty()) {
            final Utils.CrowDistances cds = calculateCrowdingDistances(members, members.get(0).getObjectives().length);
            cdMembers = Collections.unmodifiableList(cds.getIndividuals());
            sortedObjectives = Collections.unmodifiableList(cds.getSortedObjectives());
        } else {
            cdMembers = Collections.emptyList();
            sortedObjectives = Collections.emptyList();
        }
    }

    @Override
    public List<CDIndividual> getMembersWithCD() {
        return cdMembers;
    }
}
