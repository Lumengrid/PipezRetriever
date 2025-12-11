package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Intercept shouldWork at RETURN so the original redstone check runs first.
 * If redstone allows work AND retrieve mode is active, we store the result
 * and return false to skip original extract logic.
 * Our TAIL injection will check the stored result.
 */
@Mixin(PipeLogicTileEntity.class)
public abstract class PipeLogicTileEntityMixin implements IRetrieveShouldWork {
    
    /**
     * Store which sides passed the redstone check but are in retrieve mode.
     * Key: side, Value: true if shouldWork would have returned true (redstone OK)
     */
    @Unique
    private final Map<Direction, Boolean> pipezretriever$retrieveShouldWork = new EnumMap<>(Direction.class);
    
    @Inject(method = "shouldWork", at = @At("RETURN"), cancellable = true, remap = false)
    private void pipezretriever$shouldWork(Direction side, PipeType<?, ?> pipeType, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IRetrieveMode retrieveMode) {
            if (retrieveMode.isRetrieving(side)) {
                // Store the original result (includes redstone check)
                boolean originalResult = cir.getReturnValue();
                pipezretriever$retrieveShouldWork.put(side, originalResult);
                
                // Return false to skip original extract logic
                // Our retrieve logic will check pipezretriever$shouldRetrieve
                cir.setReturnValue(false);
            }
        }
    }
    
    @Override
    @Unique
    public boolean pipezretriever$shouldRetrieve(Direction side) {
        return pipezretriever$retrieveShouldWork.getOrDefault(side, false);
    }
}
