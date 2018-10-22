package ru.ifmo.nds.ndt;

import java.util.Objects;

public class NdtSettings implements INdtSettings {
    private final int bucketCapacity;
    private final int dimensionsCount;

    public NdtSettings(int bucketCapacity, int dimensionsCount) {
        this.bucketCapacity = bucketCapacity;
        this.dimensionsCount = dimensionsCount;
    }

    @Override
    public int getBucketCapacity() {
        return bucketCapacity;
    }

    @Override
    public int getDimensionsCount() {
        return dimensionsCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NdtSettings that = (NdtSettings) o;
        return bucketCapacity == that.bucketCapacity &&
                dimensionsCount == that.dimensionsCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketCapacity, dimensionsCount);
    }

    @Override
    public String toString() {
        return "NdtSettings{" + "bucketCapacity=" + bucketCapacity +
                ", dimensionsCount=" + dimensionsCount +
                '}';
    }
}
