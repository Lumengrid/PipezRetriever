package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import com.lumengrid.pipezretriever.RetrieveHelper;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;

@Mixin(PipeLogicTileEntity.class)
public abstract class PipeLogicTileEntityMixin implements IRetrieveShouldWork {
    
    @Shadow(remap = false)
    public abstract boolean hasType(PipeType<?, ?> type);
    
    @Unique
    private final Map<Direction, Boolean> pipezretriever$retrieveShouldWork = new EnumMap<>(Direction.class);
    
    @Inject(method = "shouldWork", at = @At("RETURN"), cancellable = true, remap = false)
    private void pipezretriever$shouldWork(Direction side, PipeType<?, ?> pipeType, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IRetrieveMode retrieveMode) {
            if (retrieveMode.isRetrieving(side)) {
                boolean originalResult = cir.getReturnValue();
                pipezretriever$retrieveShouldWork.put(side, originalResult);
                cir.setReturnValue(false);
            }
        }
    }
    
    /**
     * Inject at the end of tick() to handle energy retrieve mode.
     * PipeEnergyStorage.tick() only runs pullEnergy if energy was received,
     * but in retrieve mode we need to actively pull regardless.
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void pipezretriever$tick(CallbackInfo ci) {
        if (!hasType(EnergyPipeType.INSTANCE)) {
            return;
        }
        
        if (!((Object) this instanceof IRetrieveMode retrieveMode)) {
            return;
        }
        
        PipeLogicTileEntity self = (PipeLogicTileEntity) (Object) this;
        
        for (Direction side : Direction.values()) {
            // Use self to call isExtracting since it's in parent class
            if (!self.isExtracting(side)) continue;
            if (!retrieveMode.isRetrieving(side)) continue;
            
            // Check redstone
            if (!RetrieveHelper.checkRedstone(self, side, EnergyPipeType.INSTANCE)) {
                continue;
            }
            
            RetrieveHelper.retrieveEnergy(EnergyPipeType.INSTANCE, self, side);
        }
    }
    
    @Override
    @Unique
    public boolean pipezretriever$shouldRetrieve(Direction side) {
        return pipezretriever$retrieveShouldWork.getOrDefault(side, false);
    }
}
