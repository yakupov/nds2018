package ru.ifmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.ndt.NdtManagedPopulation;
import ru.ifmo.nds.ndt.NdtSettings;

public class NdtCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected IManagedPopulation constructPopulation(int dimensionsCount) {
        return new NdtManagedPopulation(new NdtSettings(2, dimensionsCount));
    }
}
