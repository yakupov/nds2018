package ru.ifmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.enlu.ENLUManagedPopulation;

public class ENLUCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected IManagedPopulation<Object> constructPopulation(int dimensionsCount) {
        return new ENLUManagedPopulation<>(Long.MAX_VALUE);
    }
}
