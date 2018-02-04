package ru.ifmo.nds.dcns.concurrent;

public class SyncJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    protected AbstractConcurrentJFBYPopulation constructPopulation(int dimensionsCount) {
        return new SyncJFBYPopulation();
    }
}
