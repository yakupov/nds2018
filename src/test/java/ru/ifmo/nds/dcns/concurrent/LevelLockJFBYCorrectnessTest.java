package ru.ifmo.nds.dcns.concurrent;

public class LevelLockJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    protected AbstractConcurrentJFBYPopulation constructPopulation(int dimensionsCount) {
        return new LevelLockJFBYPopulation();
    }
}
