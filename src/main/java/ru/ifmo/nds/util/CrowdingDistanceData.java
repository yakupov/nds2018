package ru.ifmo.nds.util;

import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.dcns.jfby.SortedObjectives;

import java.util.List;
import java.util.Objects;

public class CrowdingDistanceData<T> {
    private final List<IIndividual<T>> individuals; //Sorted lexicographically
    private final SortedObjectives<T> sortedObjectives; //Sorted only by corresponding objective

    public CrowdingDistanceData(List<IIndividual<T>> individuals, SortedObjectives<T> sortedObjectives) {
        this.individuals = individuals;
        this.sortedObjectives = sortedObjectives;
    }

    public List<IIndividual<T>> getIndividuals() {
        return individuals;
    }

    public SortedObjectives<T> getSortedObjectives() {
        return sortedObjectives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrowdingDistanceData that = (CrowdingDistanceData) o;
        return Objects.equals(individuals, that.individuals) &&
                Objects.equals(sortedObjectives, that.sortedObjectives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(individuals, sortedObjectives);
    }

    @Override
    public String toString() {
        return "CrowdingDistanceData{" +
                "individuals=" + individuals +
                ", sortedObjectives=" + sortedObjectives +
                '}';
    }
}
