package ru.ifmo.nds.dcns.concurrent;

public class NormalCJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    boolean shouldUseOneByOneSorting() {
        return false;
    }
}
