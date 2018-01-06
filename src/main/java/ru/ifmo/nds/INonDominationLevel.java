package ru.ifmo.nds;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Non-domination level
 */
public interface INonDominationLevel {
    /**
     * @return Lexicographically sorted members of this layer
     */
    List<IIndividual> getMembers();

    /**
     * Add new points (assuming that their ranks equal the rank of this level).
     *
     * @param addends New points
     * @return A set of evicted points that should be moved to the next level
     */
    List<IIndividual> addMembers(@Nonnull List<IIndividual> addends);

    /**
     * @return true if {@code point} is dominated by any member of this layer
     */
    boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point);

    /**
     * @return Shallow copy of this layer
     */
    INonDominationLevel copy();
}
