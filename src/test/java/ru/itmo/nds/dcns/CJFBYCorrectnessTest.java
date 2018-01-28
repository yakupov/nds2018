package ru.itmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.concurrent.CJFBYPopulation;

public class CJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    IManagedPopulation constructPopulation(int dimensionsCount) {
        return new CJFBYPopulation(100500);
    }
}
