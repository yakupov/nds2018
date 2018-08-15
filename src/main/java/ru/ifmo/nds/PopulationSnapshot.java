package ru.ifmo.nds;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Immutable
public class PopulationSnapshot {
    private final List<INonDominationLevel> levels;
    private final int size;

    public PopulationSnapshot(List<INonDominationLevel> levels, int size) {
        this.levels = levels;
        this.size = size;
    }

    public List<INonDominationLevel> getLevels() {
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
