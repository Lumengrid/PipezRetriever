package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.IRetrieveShouldWork;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;

@Mixin(PipeLogicTileEntity.class)
public abstract class PipeLogicTileEntityMixin implements IRetrieveShouldWork {
    
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
    
    @Override
    @Unique
    public boolean pipezretriever$shouldRetrieve(Direction side) {
        return pipezretriever$retrieveShouldWork.getOrDefault(side, false);
    }
}
