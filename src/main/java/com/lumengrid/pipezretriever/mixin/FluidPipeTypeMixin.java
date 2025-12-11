package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidPipeType.class)
public abstract class FluidPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$tick(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        PipezRetriever.LOGGER.info("[FLUID Mixin] tick() called at pos {}", tileEntity.getBlockPos());
        
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[FLUID Mixin] tileEntity is NOT IRetrieveMode, letting original handle");
            return;
        }
        
        PipezRetriever.LOGGER.info("[FLUID Mixin] tileEntity IS IRetrieveMode");
        
        boolean hasRetrieveMode = false;
        for (Direction side : Direction.values()) {
            if (tileEntity.isExtracting(side)) {
                boolean isRetrieving = retrieveMode.isRetrieving(side);
                PipezRetriever.LOGGER.info("[FLUID Mixin] Side {} - extracting=true, retrieving={}", side, isRetrieving);
                if (isRetrieving) {
                    hasRetrieveMode = true;
                }
            }
        }
        
        if (!hasRetrieveMode) {
            PipezRetriever.LOGGER.info("[FLUID Mixin] No retrieve mode on any side, letting original handle");
            return;
        }
        
        PipezRetriever.LOGGER.info("[FLUID Mixin] RETRIEVE MODE ACTIVE - handling with RetrieveHelper");
        
        FluidPipeType pipeType = (FluidPipeType) (Object) this;
        RetrieveHelper.tickFluidPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        ci.cancel();
    }
}
