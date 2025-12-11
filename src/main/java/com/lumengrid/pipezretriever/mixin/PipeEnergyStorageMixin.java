package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.utils.PipeEnergyStorage;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PipeEnergyStorage.class)
public abstract class PipeEnergyStorageMixin {
    
    @Shadow(remap = false)
    @Final
    protected PipeLogicTileEntity pipe;
    
    @Shadow(remap = false)
    @Final
    protected Direction side;
    
    /**
     * Intercept tick to handle retrieve mode.
     * In retrieve mode, we always want to actively pull energy,
     * regardless of whether energy was recently received.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$tick(CallbackInfo ci) {
        PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] tick() called at pos {}, side {}", pipe.getBlockPos(), side);
        
        if (!(pipe instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] pipe is NOT IRetrieveMode, letting original handle");
            return; // Let original handle it
        }
        
        PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] pipe IS IRetrieveMode");
        
        // Only intercept if in retrieve mode
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] Side {} - isRetrieving={}", side, isRetrieving);
        
        if (!isRetrieving) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] Not in retrieve mode, letting original handle");
            return; // Let original handle normal mode
        }
        
        PipezRetriever.LOGGER.info("[ENERGY STORAGE Mixin] RETRIEVE MODE ACTIVE - handling with RetrieveHelper");
        
        // In retrieve mode: always call our retrieve logic, ignoring lastReceived
        RetrieveHelper.pullEnergyWithRetrieve(EnergyPipeType.INSTANCE, pipe, side);
        
        // Cancel the original tick since we handled it
        ci.cancel();
    }
}

