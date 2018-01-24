package ru.itmo.nds.dcns;

import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.concurrent.CJFBYPopulation;
import ru.ifmo.nds.dcns.jfby.JFBYPopulation;
import ru.ifmo.nds.dcns.sorter.JFB2014;

public class CJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    IManagedPopulation constructPopulation(int dimensionsCount) {
        return new CJFBYPopulation(new JFB2014(), 100500);
    }
}
