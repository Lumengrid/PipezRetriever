package com.lumengrid.pipezretriever;

import net.minecraft.core.Direction;

/**
 * Interface to check if retrieve mode should work for a side.
 * This stores the result of the original shouldWork check (including redstone).
 */
public interface IRetrieveShouldWork {
    
    /**
     * Check if retrieve mode should work for this side.
     * Returns true if the original shouldWork returned true (redstone OK).
     */
    boolean pipezretriever$shouldRetrieve(Direction side);
}

