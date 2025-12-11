package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
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
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        boolean hasRetrieveMode = false;
        for (Direction side : Direction.values()) {
            if (tileEntity.isExtracting(side) && retrieveMode.pipezretriever$isRetrieving(side)) {
                hasRetrieveMode = true;
                break;
            }
        }
        
        if (!hasRetrieveMode) {
            return;
        }
        
        FluidPipeType pipeType = (FluidPipeType) (Object) this;
        RetrieveHelper.tickFluidPipeWithRetrieve(pipeType, tileEntity, retrieveMode);
        ci.cancel();
    }
}
