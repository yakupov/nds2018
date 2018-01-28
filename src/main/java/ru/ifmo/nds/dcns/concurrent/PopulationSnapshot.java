package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.INonDominationLevel;

import java.util.List;
import java.util.Objects;

class PopulationSnapshot {
    private final List<INonDominationLevel> levels;
    private final int size;

    PopulationSnapshot(List<INonDominationLevel> levels, int size) {
        this.levels = levels;
        this.size = size;
    }

    List<INonDominationLevel> getLevels() {
        return levels;
    }

    public int getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PopulationSnapshot that = (PopulationSnapshot) o;
        return size == that.size &&
                Objects.equals(levels, that.levels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(levels, size);
    }

    @Override
    public String toString() {
        return "PopulationSnapshot{" + "levels=" + levels +
                ", size=" + size +
                '}';
    }
}
