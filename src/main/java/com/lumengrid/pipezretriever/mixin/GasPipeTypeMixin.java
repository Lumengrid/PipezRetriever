package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GasPipeType.class)
public abstract class GasPipeTypeMixin {
    
    /**
     * Intercept tick to handle retrieve mode for gas/chemical pipes.
     * In retrieve mode: pull FROM connections INTO the source
     * In normal mode: push FROM source TO connections
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$tick(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        // Only apply if the tile entity supports retrieve mode
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        GasPipeType pipeType = (GasPipeType) (Object) this;
        RetrieveHelper.tickGasPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        
        // Cancel the original method since we handled it
        ci.cancel();
    }
}

