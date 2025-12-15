package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
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

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void pipezretriever$tick(CallbackInfo ci) {
        
        if (!(pipe instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        if (!isRetrieving) {
            return;
        }
        
        if (!pipe.isExtracting(side)) {
            ci.cancel();
            return;
        }
        
        boolean redstoneOK = RetrieveHelper.checkRedstone(pipe, side, EnergyPipeType.INSTANCE);
        if (!redstoneOK) {
            ci.cancel();
            return;
        }

        RetrieveHelper.retrieveEnergy(EnergyPipeType.INSTANCE, pipe, side);
        ci.cancel();
    }
}
