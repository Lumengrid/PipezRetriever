package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject at TAIL of tick to add retrieve logic.
 * Original extract logic is already skipped for retrieve-mode sides via PipeLogicTileEntityMixin.
 * We use IRetrieveShouldWork to check if redstone allows operation.
 */
@Mixin(FluidPipeType.class)
public abstract class FluidPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pipezretriever$tickRetrieve(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        if (!(tileEntity instanceof IRetrieveShouldWork shouldWork)) {
            return;
        }
        
        FluidPipeType pipeType = (FluidPipeType) (Object) this;
        
        for (Direction side : Direction.values()) {
            if (!tileEntity.isExtracting(side)) continue;
            if (!retrieveMode.isRetrieving(side)) continue;
            
            // Use the stored result from shouldWork (includes redstone check)
            if (!shouldWork.pipezretriever$shouldRetrieve(side)) continue;
            
            RetrieveHelper.retrieveFluids(pipeType, tileEntity, side);
        }
    }
}
