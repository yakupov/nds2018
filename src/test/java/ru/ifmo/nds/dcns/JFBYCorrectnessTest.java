package ru.ifmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.jfby.JFBYPopulation;

public class JFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected IManagedPopulation constructPopulation(int dimensionsCount) {
        return new JFBYPopulation(Long.MAX_VALUE);
    }
}
