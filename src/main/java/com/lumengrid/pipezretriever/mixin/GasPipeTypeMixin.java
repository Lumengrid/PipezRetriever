package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import net.minecraft.core.Direction;
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
        PipezRetriever.LOGGER.info("[GAS Mixin] tick() called at pos {}", tileEntity.getBlockPos());
        
        // Only apply if the tile entity supports retrieve mode
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[GAS Mixin] tileEntity is NOT IRetrieveMode, letting original handle");
            return;
        }
        
        PipezRetriever.LOGGER.info("[GAS Mixin] tileEntity IS IRetrieveMode");
        
        // Log retrieve status for each side
        for (Direction side : Direction.values()) {
            if (tileEntity.isExtracting(side)) {
                boolean isRetrieving = retrieveMode.isRetrieving(side);
                PipezRetriever.LOGGER.info("[GAS Mixin] Side {} - extracting=true, retrieving={}", side, isRetrieving);
            }
        }
        
        PipezRetriever.LOGGER.info("[GAS Mixin] Handling with RetrieveHelper");
        
        GasPipeType pipeType = (GasPipeType) (Object) this;
        RetrieveHelper.tickGasPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        
        // Cancel the original method since we handled it
        ci.cancel();
    }
}

