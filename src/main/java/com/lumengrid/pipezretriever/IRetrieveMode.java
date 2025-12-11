package com.lumengrid.pipezretriever;

import net.minecraft.core.Direction;

/**
 * Interface for pipes that support retrieve mode.
 * When retrieve mode is enabled, the pipe pulls from connected inventories
 * into the source inventory (opposite of normal extract behavior).
 */
public interface IRetrieveMode {
    
    /**
     * Check if retrieve mode is enabled for the given side
     * @param side The direction to check
     * @return true if retrieve mode is enabled
     */
    boolean pipezretriever$isRetrieving(Direction side);
    
    /**
     * Set retrieve mode for the given side
     * @param side The direction to set
     * @param retrieving Whether to enable retrieve mode
     */
    void pipezretriever$setRetrieving(Direction side, boolean retrieving);
    
    /**
     * Get the retrieve mode array
     * @return Array of retrieve mode states for each direction
     */
    boolean[] pipezretriever$getRetrievingSides();
}

