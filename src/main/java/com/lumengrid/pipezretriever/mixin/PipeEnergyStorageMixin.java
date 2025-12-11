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
        PipezRetriever.LOGGER.info("[Energy] PipeEnergyStorage.tick() 1 side {}", side);
        if (!(pipe instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[Energy] PipeEnergyStorage.tick() 2 side {}", side);
            return; // Let original handle it
        }
        
        // Only intercept if in retrieve mode
        PipezRetriever.LOGGER.info("[Energy] PipeEnergyStorage.tick() 3 side {}", side);
        if (!retrieveMode.pipezretriever$isRetrieving(side)) {
            PipezRetriever.LOGGER.info("[Energy] PipeEnergyStorage.tick() 4 side {}", side);
            return; // Let original handle normal mode
        }
        
        PipezRetriever.LOGGER.info("[Energy] PipeEnergyStorage.tick() - RETRIEVE MODE active for side {}", side);
        
        // In retrieve mode: always call our retrieve logic, ignoring lastReceived
        RetrieveHelper.pullEnergyWithRetrieve(EnergyPipeType.INSTANCE, pipe, side);
        
        // Cancel the original tick since we handled it
        ci.cancel();
    }
}

