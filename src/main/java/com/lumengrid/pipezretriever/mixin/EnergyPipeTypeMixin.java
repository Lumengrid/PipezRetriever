package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
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
        PipezRetriever.LOGGER.info("[ENERGY] pullEnergy called for side {}", side);
        
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            PipezRetriever.LOGGER.info("[ENERGY] Not IRetrieveMode, letting original run");
            return;
        }
        
        boolean isRetrieving = retrieveMode.isRetrieving(side);
        PipezRetriever.LOGGER.info("[ENERGY] Side {} isRetrieving={}", side, isRetrieving);
        
        if (!isRetrieving) {
            PipezRetriever.LOGGER.info("[ENERGY] Not retrieve mode, letting original run");
            return;
        }
        
        if (!tileEntity.isExtracting(side)) {
            PipezRetriever.LOGGER.info("[ENERGY] Not extracting, cancelling");
            ci.cancel();
            return;
        }
        
        boolean redstoneOK = RetrieveHelper.checkRedstone(tileEntity, side, EnergyPipeType.INSTANCE);
        PipezRetriever.LOGGER.info("[ENERGY] Redstone check: {}", redstoneOK);
        
        if (!redstoneOK) {
            PipezRetriever.LOGGER.info("[ENERGY] Redstone blocked, cancelling");
            ci.cancel();
            return;
        }
        
        PipezRetriever.LOGGER.info("[ENERGY] Calling retrieveEnergy");
        EnergyPipeType pipeType = (EnergyPipeType) (Object) this;
        RetrieveHelper.retrieveEnergy(pipeType, tileEntity, side);
        
        ci.cancel();
    }
}
