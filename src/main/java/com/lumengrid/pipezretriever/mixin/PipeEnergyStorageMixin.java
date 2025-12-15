package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
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
        PipezRetriever.LOGGER.info("[ENERGY STORAGE] tick called for side {}", side);
        
        if (!(pipe instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE] Not IRetrieveMode, letting original run");
            return;
        }
        
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        PipezRetriever.LOGGER.info("[ENERGY STORAGE] Side {} isRetrieving={}", side, isRetrieving);
        
        if (!isRetrieving) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE] Not retrieve mode, letting original run");
            return;
        }
        
        if (!pipe.isExtracting(side)) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE] Not extracting, cancelling");
            ci.cancel();
            return;
        }
        
        boolean redstoneOK = RetrieveHelper.checkRedstone(pipe, side, EnergyPipeType.INSTANCE);
        PipezRetriever.LOGGER.info("[ENERGY STORAGE] Redstone check: {}", redstoneOK);
        
        if (!redstoneOK) {
            PipezRetriever.LOGGER.info("[ENERGY STORAGE] Redstone blocked, cancelling");
            ci.cancel();
            return;
        }
        
        PipezRetriever.LOGGER.info("[ENERGY STORAGE] Calling retrieveEnergy");
        RetrieveHelper.retrieveEnergy(EnergyPipeType.INSTANCE, pipe, side);
        
        ci.cancel();
    }
}
