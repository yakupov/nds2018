package ru.ifmo.nds.dcns.jfby;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;
import ru.ifmo.nds.util.CrowdingDistanceData;
import ru.ifmo.nds.util.ObjectiveComparator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SortedObjectives<T> {
    private final int objCount;
    private final int popSize;
    private final Object[][] data;

    public static <T1> SortedObjectives<T1> empty(final int objCount) {
        return new SortedObjectives<>(objCount, 0, new Object[objCount][0]);
    }

    public static <T> CrowdingDistanceData<T> calculateCrowdingDistances(final int objCount,
                                                                         @Nonnull final List<IIndividual<T>> members) {
        final int n = members.size();

        final IIndividual[] frontCopy = new IIndividual[members.size()];
        for (int i = 0; i < members.size(); ++i) {
            frontCopy[i] = members.get(i);
        }

        final Object[][] sortedObj = new Object[objCount][n];

        final Map<IIndividual<T>, Double> cdMap = new IdentityHashMap<>();
        for (int obj = 0; obj < objCount; obj++) {
            Arrays.sort(frontCopy, new ObjectiveComparator(obj));
            for (int i = 0; i < members.size(); ++i) {
                sortedObj[obj][i] = frontCopy[i];
            }

            cdMap.put(frontCopy[0], Double.POSITIVE_INFINITY);
            cdMap.put(frontCopy[n - 1], Double.POSITIVE_INFINITY);

            final double minObjective = frontCopy[0].getObjectives()[obj];
            final double maxObjective = frontCopy[n - 1].getObjectives()[obj];
            for (int j = 1; j < n - 1; j++) {
                double distance = cdMap.getOrDefault(frontCopy[j], 0.0);
                distance += (frontCopy[j + 1].getObjectives()[obj] -
                        frontCopy[j - 1].getObjectives()[obj])
                        / (maxObjective - minObjective);
                cdMap.put(frontCopy[j], distance);
            }
        }

        final List<IIndividual<T>> rs = new ArrayList<>();
        for (IIndividual<T> member : members) {
            rs.add(new FitnessAndCdIndividual<>(member.getObjectives(), cdMap.get(member), member.getPayload()));
        }
        return new CrowdingDistanceData<>(rs, new SortedObjectives<>(objCount, n, sortedObj));
    }

    private SortedObjectives(int objCount, int popSize, Object[][] data) {
        this.objCount = objCount;
        this.popSize = popSize;
        this.data = data;
    }

    public int getObjCount() {
        return objCount;
    }

    public int getPopSize() {
        return popSize;
    }

    public IIndividual<T> get(final int objNumber, final int index) {
        //TODO: check maybe? at least for ext calls
        return (IIndividual<T>) data[objNumber][index];
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public SortedObjectives<T> addAndRemove(@Nonnull Set<IIndividual<T>> removed,
                                            @Nonnull IIndividual[] addends) {
        final int targetSize = popSize - removed.size() + addends.length;
        final Object[][] newData = new Object[objCount][targetSize];

        for (int objNumber = 0; objNumber < objCount; ++objNumber) {
            int cAddends = 0;
            int cOldSorted = 0;
            int cNewData = 0;
            final ObjectiveComparator comparator = new ObjectiveComparator(objNumber);
            Arrays.sort(addends, comparator);
            //TODO: check payload type maybe
            while (cNewData < targetSize) {
                if (cOldSorted >= popSize) {
                    newData[objNumber][cNewData++] = addends[cAddends++];
                } else if (cAddends >= addends.length) {
                    final Object individual = data[objNumber][cOldSorted];
                    if (!removed.contains(individual)) {
                        newData[objNumber][cNewData++] = individual;
                    }
                    ++cOldSorted;
                } else if (comparator.compare(addends[cAddends], get(objNumber, cOldSorted)) <= 0) {
                    newData[objNumber][cNewData++] = addends[cAddends++];
                } else {
                    final Object individual = data[objNumber][cOldSorted];
                    if (!removed.contains(individual)) {
                        newData[objNumber][cNewData++] = individual;
                    }
                    ++cOldSorted;
                }
            }
        }

        return new SortedObjectives<>(objCount, targetSize, newData);
    }

    public void updateCdMap(@Nonnull final double[] mins,
                            @Nonnull final double[] maxs,
                            @Nonnull final Object2DoubleMap<IIndividual<T>> cdMap) {
        for (int obj = 0; obj < objCount; ++obj) {
            cdMap.put((IIndividual<T>) data[obj][0], Double.POSITIVE_INFINITY);
            cdMap.put((IIndividual<T>) data[obj][popSize - 1], Double.POSITIVE_INFINITY);

            final double inverseDelta = 1 / (maxs[obj] - mins[obj]);
            for (int j = 1; j < popSize - 1; j++) {
                double distance = cdMap.getOrDefault(data[obj][j], 0.0);
                distance += (((IIndividual<T>) data[obj][j + 1]).getObjectives()[obj] -
                        ((IIndividual<T>) data[obj][j - 1]).getObjectives()[obj]) * inverseDelta;
                cdMap.put(((IIndividual<T>) data[obj][j]), distance);
            }
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SortedObjectives<?> that = (SortedObjectives<?>) o;
        return objCount == that.objCount &&
                popSize == that.popSize &&
                Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objCount, popSize, data);
    }

    @Override
    public String toString() {
        return "SortedObjectives{" +
                "objCount=" + objCount +
                ", popSize=" + popSize +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
