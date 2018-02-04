package ru.ifmo.nds.dcns.concurrent;

public class NormalCJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    protected AbstractConcurrentJFBYPopulation constructPopulation(int dimensionsCount) {
        return new CJFBYPopulation(100500, false);
    }
}
