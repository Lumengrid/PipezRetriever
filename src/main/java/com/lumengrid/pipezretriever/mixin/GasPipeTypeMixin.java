package com.lumengrid.pipezretriever.mixin;

import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GasPipeType.class)
public abstract class GasPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pipezretriever$tickRetrieve(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        // Disattivato temporaneamente per NeoForge 26.1.2 in attesa di Mekanism
    }
}
