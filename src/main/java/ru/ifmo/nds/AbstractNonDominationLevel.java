package ru.ifmo.nds;

import ru.ifmo.nds.impl.CDIndividual;
import ru.ifmo.nds.util.Utils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ru.ifmo.nds.util.Utils.calculateCrowdingDistances;

public abstract class AbstractNonDominationLevel implements INonDominationLevel {
    protected volatile List<CDIndividual> cdMembers = null;
    protected volatile List<List<IIndividual>> sortedObjectives = null;

    //@Override
    public List<CDIndividual> getMembersWithCD0() {
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

    private final Lock lock = new ReentrantLock();

    //@Override
    public List<CDIndividual> getMembersWithCD() {
        final List<IIndividual> members = getMembers();
        while (true) {
            if (cdMembers != null) {
                return cdMembers;
            } else {
                if (lock.tryLock()) {
                    try {
                        if (members.isEmpty()) {
                            cdMembers = Collections.emptyList();
                        } else {
                            final Utils.CrowDistances cds = calculateCrowdingDistances(members, members.get(0).getObjectives().length);
                            cdMembers = Collections.unmodifiableList(cds.getIndividuals());
                            sortedObjectives = Collections.unmodifiableList(cds.getSortedObjectives());
                        }
                        return cdMembers;
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }
}
