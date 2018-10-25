package ru.ifmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.jfby.TotalSyncJFBYPopulation;

public class TotalSyncJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected IManagedPopulation<Object> constructPopulation(int dimensionsCount) {
        return new TotalSyncJFBYPopulation<>(Long.MAX_VALUE);
    }
}
