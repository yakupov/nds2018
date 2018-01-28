package ru.ifmo.nds.dcns.concurrent;

import org.junit.Test;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.IManagedPopulation;
import ru.ifmo.nds.dcns.ManagedPopulationCorrectnessTest;
import ru.ifmo.nds.impl.FitnessOnlyIndividual;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

abstract class AbstractCJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    abstract boolean shouldUseOneByOneSorting();

    @Override
    protected IManagedPopulation constructPopulation(int dimensionsCount) {
        return new CJFBYPopulation(100500, shouldUseOneByOneSorting());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testWithRemoval() {
        final CJFBYPopulation pop = new CJFBYPopulation(100500, shouldUseOneByOneSorting());

        final double[] point16 = {0.1669424402868558, 0.41123196219828895, 17.98980401569634};
        pop.addIndividual(new FitnessOnlyIndividual(point16));
        assertEquals(1, pop.getLevels().size());
        assertEquals(1, pop.getLevels().get(0).getMembers().size());

        pop.addIndividual(new FitnessOnlyIndividual(new double[]{0.917634913762617, 0.9778742572218526, 16.9584650345564}));
        assertEquals(1, pop.getLevels().size());
        assertEquals(2, pop.getLevels().get(0).getMembers().size());

        final double[] point88 = {0.8868046448171203, 0.5802605728140939, 18.793267306998885};
        pop.addIndividual(new FitnessOnlyIndividual(point88));
        assertEquals(2, pop.getLevels().size());
        assertEquals(2, pop.getLevels().get(0).getMembers().size());
        assertEquals(1, pop.getLevels().get(1).getMembers().size());
        assertArrayEquals(point88, pop.getLevels().get(1).getMembers().get(0).getObjectives(), 0.0);

        pop.addIndividual(new FitnessOnlyIndividual(new double[]{0.40892166575913325, 0.026280324605388206, 21.255937437050655}));
        assertEquals(2, pop.getLevels().size());
        assertEquals(3, pop.getLevels().get(0).getMembers().size());
        assertEquals(1, pop.getLevels().get(1).getMembers().size());

        final double[] point63 = {0.6305014841432228, 0.5990732774500678, 18.139060039219498};
        pop.addIndividual(new FitnessOnlyIndividual(point63));
        assertEquals(2, pop.getLevels().size());
        assertEquals(3, pop.getLevels().get(0).getMembers().size());

        List<IIndividual> level1 = pop.getLevels().get(1).getMembers();
        assertEquals(2, level1.size());
        assertEquals(
                Arrays.asList(point63, point88),
                level1.stream().map(IIndividual::getObjectives).collect(Collectors.toList())
        );

        assertArrayEquals(point63, pop.intRemoveWorst().getObjectives(), 0.000001);
        assertEquals(1, pop.getLevels().get(1).getMembers().size());
        assertArrayEquals(point88, pop.getLevels().get(1).getMembers().get(0).getObjectives(), 0.000001);

        assertArrayEquals(point88, pop.intRemoveWorst().getObjectives(), 0.000001);
        assertEquals(1, pop.getLevels().size());
        assertEquals(3, pop.getLevels().get(0).getMembers().size());

        final double[] point1 = {0.1, 0.9, 22.2};
        pop.addIndividual(new FitnessOnlyIndividual(point1));
        assertEquals(1, pop.getLevels().size());
        assertEquals(4, pop.getLevels().get(0).getMembers().size());

        assertArrayEquals(point16, pop.intRemoveWorst().getObjectives(), 0.000001);
        assertEquals(1, pop.getLevels().size());
        assertEquals(3, pop.getLevels().get(0).getMembers().size());
    }
}
