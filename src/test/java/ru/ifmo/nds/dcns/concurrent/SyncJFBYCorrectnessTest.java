package ru.ifmo.nds.dcns.concurrent;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.ManagedPopulationCorrectnessTest;
import ru.ifmo.nds.dcns.jfby.JFBYPopulation;

public class SyncJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected IManagedPopulation constructPopulation(int dimensionsCount) {
        return new SyncJFBYPopulation();
    }
}
