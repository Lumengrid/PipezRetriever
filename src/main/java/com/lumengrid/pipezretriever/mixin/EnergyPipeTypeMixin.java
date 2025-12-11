package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
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
    
    @Inject(method = "pullEnergy", at = @At("TAIL"), remap = false)
    private void pipezretriever$pullEnergyRetrieve(PipeLogicTileEntity tileEntity, Direction side, CallbackInfo ci) {
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        if (!(tileEntity instanceof IRetrieveShouldWork shouldWork)) {
            return;
        }
        
        if (!tileEntity.isExtracting(side)) return;
        if (!retrieveMode.isRetrieving(side)) return;
        if (!shouldWork.pipezretriever$shouldRetrieve(side)) return;
        
        EnergyPipeType pipeType = (EnergyPipeType) (Object) this;
        RetrieveHelper.retrieveEnergy(pipeType, tileEntity, side);
    }
}
