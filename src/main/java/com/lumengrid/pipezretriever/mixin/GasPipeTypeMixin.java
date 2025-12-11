package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import de.maxhenkel.pipez.utils.MekanismUtils;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GasPipeType.class)
public abstract class GasPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pipezretriever$tickRetrieve(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        if (!MekanismUtils.isMekanismInstalled()) return;
        
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        if (!(tileEntity instanceof IRetrieveShouldWork shouldWork)) {
            return;
        }
        
        GasPipeType pipeType = (GasPipeType) (Object) this;
        
        for (Direction side : Direction.values()) {
            if (!tileEntity.isExtracting(side)) continue;
            if (!retrieveMode.isRetrieving(side)) continue;
            if (!shouldWork.pipezretriever$shouldRetrieve(side)) continue;
            
            RetrieveHelper.retrieveGas(pipeType, tileEntity, side);
        }
    }
}
