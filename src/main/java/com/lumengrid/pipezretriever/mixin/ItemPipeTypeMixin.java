package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemPipeType.class)
public abstract class ItemPipeTypeMixin {
    
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pipezretriever$tickRetrieve(PipeLogicTileEntity tileEntity, CallbackInfo ci) {
        if (!(tileEntity instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        if (!(tileEntity instanceof IRetrieveShouldWork shouldWork)) {
            return;
        }
        
        ItemPipeType pipeType = (ItemPipeType) (Object) this;
        
        for (Direction side : Direction.values()) {
            if (!tileEntity.isExtracting(side)) continue;
            if (!retrieveMode.isRetrieving(side)) continue;
            
            int speed = pipeType.getSpeed(tileEntity.getUpgrade(side));
            if (tileEntity.getLevel().getGameTime() % speed != 0) continue;
            
            if (!shouldWork.pipezretriever$shouldRetrieve(side)) continue;
            
            RetrieveHelper.retrieveItems(pipeType, tileEntity, side);
        }
    }
}
