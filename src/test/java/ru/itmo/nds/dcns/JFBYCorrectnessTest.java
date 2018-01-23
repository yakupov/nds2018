package ru.itmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.jfby.JFBYPopulation;

public class JFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    IManagedPopulation constructPopulation(int dimensionsCount) {
        return new JFBYPopulation();
    }
}
