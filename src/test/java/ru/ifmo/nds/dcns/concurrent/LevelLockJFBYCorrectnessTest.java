package ru.ifmo.nds.dcns.concurrent;

import org.junit.Test;
import ru.ifmo.nds.IIndividual;
import ru.ifmo.nds.dcns.ManagedPopulationCorrectnessTest;
import ru.ifmo.nds.impl.FitnessAndCdIndividual;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LevelLockJFBYCorrectnessTest extends ManagedPopulationCorrectnessTest {
    @Override
    protected LevelLockJFBYPopulation constructPopulation(int dimensionsCount) {
        return new LevelLockJFBYPopulation();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testWithRemoval() {
        final LevelLockJFBYPopulation<Object> pop = new LevelLockJFBYPopulation<>(4, 1);

        final double[] point16 = {0.1669424402868558, 0.41123196219828895, 17.98980401569634};
        pop.addIndividual(new FitnessAndCdIndividual<>(point16, null));
        assertEquals(1, pop.getSnapshot().getLevels().size());
        assertEquals(1, pop.getSnapshot().getLevels().get(0).getMembers().size());

        pop.addIndividual(new FitnessAndCdIndividual<>(new double[]{0.917634913762617, 0.9778742572218526, 16.9584650345564}, null));
        assertEquals(1, pop.getSnapshot().getLevels().size());
        assertEquals(2, pop.getSnapshot().getLevels().get(0).getMembers().size());

        final double[] point88 = {0.8868046448171203, 0.5802605728140939, 18.793267306998885};
        pop.addIndividual(new FitnessAndCdIndividual<>(point88, null));
        assertEquals(2, pop.getSnapshot().getLevels().size());
        assertEquals(2, pop.getSnapshot().getLevels().get(0).getMembers().size());
        assertEquals(1, pop.getSnapshot().getLevels().get(1).getMembers().size());
        assertArrayEquals(point88, pop.getSnapshot().getLevels().get(1).getMembers().get(0).getObjectives(), 0.0);

        pop.addIndividual(new FitnessAndCdIndividual<>(new double[]{0.40892166575913325, 0.026280324605388206, 21.255937437050655}, null));
        assertEquals(2, pop.getSnapshot().getLevels().size());
        assertEquals(3, pop.getSnapshot().getLevels().get(0).getMembers().size());
        assertEquals(1, pop.getSnapshot().getLevels().get(1).getMembers().size());

        final double[] point63 = {0.6305014841432228, 0.5990732774500678, 18.139060039219498};
        pop.addIndividual(new FitnessAndCdIndividual<>(point63, null));
        assertEquals(2, pop.getSnapshot().getLevels().size());
        assertEquals(3, pop.getSnapshot().getLevels().get(0).getMembers().size());

        List<IIndividual<Object>> level1 = pop.getSnapshot().getLevels().get(1).getMembers();
        assertEquals(1, level1.size());
        assertEquals(
                Collections.singletonList(point88),
                level1.stream().map(IIndividual::getObjectives).collect(Collectors.toList())
        );

        final double[] point1 = {0.1, 0.9, 22.2};
        pop.addIndividual(new FitnessAndCdIndividual<>(point1, null));
        assertEquals(1, pop.getSnapshot().getLevels().size());
        assertEquals(4, pop.getSnapshot().getLevels().get(0).getMembers().size());
    }
}
