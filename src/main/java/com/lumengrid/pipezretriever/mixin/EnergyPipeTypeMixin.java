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
    
    @Inject(method = "pullEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$pullEnergy(PipeLogicTileEntity tileEntity, Direction side, CallbackInfo ci) {
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        if (!isRetrieving) {
            return;
        }
        
        if (!tileEntity.isExtracting(side)) {
            ci.cancel();
            return;
        }
        
        boolean redstoneOK = RetrieveHelper.checkRedstone(tileEntity, side, EnergyPipeType.INSTANCE);
        if (!redstoneOK) {
            ci.cancel();
            return;
        }

        EnergyPipeType pipeType = (EnergyPipeType) (Object) this;
        RetrieveHelper.retrieveEnergy(pipeType, tileEntity, side);
        
        ci.cancel();
    }
}
