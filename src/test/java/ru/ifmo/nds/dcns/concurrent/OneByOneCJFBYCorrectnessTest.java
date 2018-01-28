package ru.ifmo.nds.dcns.concurrent;

public class OneByOneCJFBYCorrectnessTest extends AbstractCJFBYCorrectnessTest {
    @Override
    boolean shouldUseOneByOneSorting() {
        return true;
    }
}
