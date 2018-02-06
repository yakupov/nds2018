package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.Utils;

import java.util.Collections;
import java.util.List;

import static ru.ifmo.nds.util.Utils.calculateCrowdingDistances;

public abstract class AbstractNonDominationLevel implements INonDominationLevel {
    protected volatile List<CDIndividual> cdMembers = null;
    protected volatile List<List<Double>> sortedObjectives = null;

    @Override
    public List<CDIndividual> getMembersWithCD() {
        final List<IIndividual> members = getMembers();
        if (cdMembers == null) {
            synchronized (this) {
                if (cdMembers == null) {
                    if (members.isEmpty()) {
                        cdMembers = Collections.emptyList();
                    } else {
                        final Utils.CrowDistances cds = calculateCrowdingDistances(members, members.get(0).getObjectives().length);
                        cdMembers = Collections.unmodifiableList(cds.getIndividuals());
                        sortedObjectives = Collections.unmodifiableList(cds.getSortedObjectives());
                    }
                }
            }
        }
        return cdMembers;
    }
}
