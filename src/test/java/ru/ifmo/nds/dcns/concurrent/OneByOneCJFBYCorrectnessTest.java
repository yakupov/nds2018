package ru.ifmo.nds.dcns.concurrent;

public class OneByOneCJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    protected CJFBYPopulation constructPopulation(int dimensionsCount) {
        return new CJFBYPopulation(100500, true);
    }
}
