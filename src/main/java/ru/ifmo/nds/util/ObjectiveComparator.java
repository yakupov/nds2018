package ru.ifmo.nds.util;

import ru.ifmo.nds.IIndividual;

import java.util.Comparator;

public class ObjectiveComparator implements Comparator<IIndividual> {
	private final int objective;

	/**
	 * @param objective the objective to be compared, starting from zero
	 */
	public ObjectiveComparator(int objective) {
		this.objective = objective;
	}

	@Override
	public int compare(IIndividual i1, IIndividual i2) {
		return Double.compare(i1.getObjectives()[objective], i2.getObjectives()[objective]);
	}
}
