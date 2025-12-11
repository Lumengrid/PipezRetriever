package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
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
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        // Check if any side has retrieve mode enabled
        boolean hasRetrieveMode = false;
        for (Direction side : Direction.values()) {
            if (tileEntity.isExtracting(side) && retrieveMode.isRetrieving(side)) {
                hasRetrieveMode = true;
                break;
            }
        }
        
        if (!hasRetrieveMode) {
            return; // Let the original method handle it
        }
        
        // Handle retrieve mode using helper
        ItemPipeType pipeType = (ItemPipeType) (Object) this;
        RetrieveHelper.tickItemPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        ci.cancel();
    }
}
