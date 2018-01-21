package ru.itmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.lppsn.LPPSNPopulation;

public class LPPSNCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    IManagedPopulation constructPopulation(int dimensionsCount) {
        return new LPPSNPopulation();
    }
}
