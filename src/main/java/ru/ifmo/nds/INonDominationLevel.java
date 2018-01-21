package ru.ifmo.nds;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Non-domination level
 */
public interface INonDominationLevel {
    class MemberAdditionResult {
        private final List<IIndividual> evicted;
        private final INonDominationLevel modifiedLevel;

        public MemberAdditionResult(List<IIndividual> evicted, INonDominationLevel modifiedLevel) {
            this.evicted = evicted;
            this.modifiedLevel = modifiedLevel;
        }

        public List<IIndividual> getEvictedMembers() {
            return evicted;
        }

        public INonDominationLevel getModifiedLevel() {
            return modifiedLevel;
        }
    }

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
    MemberAdditionResult addMembers(@Nonnull List<IIndividual> addends);

    /**
     * @return true if {@code point} is dominated by any member of this layer
     */
    boolean dominatedByAnyPointOfThisLayer(@Nonnull IIndividual point);

    /**
     * @return Shallow copy of this layer
     */
    INonDominationLevel copy();
}
