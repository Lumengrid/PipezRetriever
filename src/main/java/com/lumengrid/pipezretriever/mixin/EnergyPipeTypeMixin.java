package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnergyPipeType.class)
public abstract class EnergyPipeTypeMixin {
    
    /**
     * Intercept pullEnergy to handle retrieve mode.
     * In retrieve mode: pull FROM connections INTO the source
     * In normal mode: let the original pipez code handle it
     */
    @Inject(method = "pullEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$pullEnergy(PipeLogicTileEntity tileEntity, Direction side, CallbackInfo ci) {
        // Only apply if the tile entity supports retrieve mode
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return; // Let original handle it
        }
        
        // Only intercept if in retrieve mode
        if (!retrieveMode.isRetrieving(side)) {
            return; // Let original pipez code handle normal mode
        }
        
        // RETRIEVE MODE: pull FROM connections INTO the source
        EnergyPipeType pipeType = (EnergyPipeType) (Object) this;
        RetrieveHelper.pullEnergyWithRetrieve(pipeType, tileEntity, side);
        
        // Cancel the original method since we handled it
        ci.cancel();
    }
}
