package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;

import java.util.Collections;
import java.util.List;

import static ru.ifmo.nds.util.Utils.calculateCrowdingDistances;

public abstract class AbstractNonDominationLevel implements INonDominationLevel {
    private volatile List<CDIndividual> cdMembers = null;

    @Override
    public List<CDIndividual> getMembersWithCD() {
        final List<IIndividual> members = getMembers();
        if (cdMembers == null) {
            synchronized (this) {
                if (cdMembers == null) {
                    if (members.isEmpty()) {
                        cdMembers = Collections.emptyList();
                    } else {
                        cdMembers = Collections.unmodifiableList(
                                calculateCrowdingDistances(members, members.get(0).getObjectives().length));
                    }
                }
            }
        }
        return cdMembers;
    }
}
