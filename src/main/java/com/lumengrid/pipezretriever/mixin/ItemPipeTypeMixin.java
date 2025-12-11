package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemPipeType.class)
public abstract class ItemPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$tick(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        PipezRetriever.LOGGER.info("[ITEM Mixin] tick() called at pos {}", tileEntity.getBlockPos());
        
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[ITEM Mixin] tileEntity is NOT IRetrieveMode, letting original handle");
            return;
        }
        
        PipezRetriever.LOGGER.info("[ITEM Mixin] tileEntity IS IRetrieveMode");
        
        // Check if any side has retrieve mode enabled
        boolean hasRetrieveMode = false;
        for (Direction side : Direction.values()) {
            if (tileEntity.isExtracting(side)) {
                boolean isRetrieving = retrieveMode.isRetrieving(side);
                PipezRetriever.LOGGER.info("[ITEM Mixin] Side {} - extracting=true, retrieving={}", side, isRetrieving);
                if (isRetrieving) {
                    hasRetrieveMode = true;
                }
            }
        }
        
        if (!hasRetrieveMode) {
            PipezRetriever.LOGGER.info("[ITEM Mixin] No retrieve mode on any side, letting original handle");
            return; // Let the original method handle it
        }
        
        PipezRetriever.LOGGER.info("[ITEM Mixin] RETRIEVE MODE ACTIVE - handling with RetrieveHelper");
        
        // Handle retrieve mode using helper
        ItemPipeType pipeType = (ItemPipeType) (Object) this;
        RetrieveHelper.tickItemPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        ci.cancel();
    }
}
