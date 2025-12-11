package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
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
        PipezRetriever.LOGGER.info("[ENERGY Mixin] pullEnergy() called at pos {}, side {}", tileEntity.getBlockPos(), side);
        
        // Only apply if the tile entity supports retrieve mode
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[ENERGY Mixin] tileEntity is NOT IRetrieveMode, letting original handle");
            return; // Let original handle it
        }
        
        PipezRetriever.LOGGER.info("[ENERGY Mixin] tileEntity IS IRetrieveMode");
        
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        PipezRetriever.LOGGER.info("[ENERGY Mixin] Side {} - isRetrieving={}", side, isRetrieving);
        
        // Only intercept if in retrieve mode
        if (!isRetrieving) {
            PipezRetriever.LOGGER.info("[ENERGY Mixin] Not in retrieve mode, letting original pipez code handle");
            return; // Let original pipez code handle normal mode
        }
        
        PipezRetriever.LOGGER.info("[ENERGY Mixin] RETRIEVE MODE ACTIVE - handling with RetrieveHelper");
        
        // RETRIEVE MODE: pull FROM connections INTO the source
        EnergyPipeType pipeType = (EnergyPipeType) (Object) this;
        RetrieveHelper.pullEnergyWithRetrieve(pipeType, tileEntity, side);
        
        // Cancel the original method since we handled it
        ci.cancel();
    }
}
