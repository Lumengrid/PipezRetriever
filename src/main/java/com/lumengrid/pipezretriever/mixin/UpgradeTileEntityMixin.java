package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UpgradeTileEntity.class)
public abstract class UpgradeTileEntityMixin {
    
    /**
     * When extraction is disabled, also disable retrieve mode
     */
    @Inject(method = "setExtracting", at = @At("HEAD"), remap = false)
    private void pipezretriever$onSetExtracting(Direction side, boolean extracting, CallbackInfo ci) {
        if (!extracting && this instanceof IRetrieveMode retrieveMode) {
            retrieveMode.setRetrieving(side, false);
        }
    }
}

