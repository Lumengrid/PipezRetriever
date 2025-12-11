package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PipeTileEntity.class)
public abstract class PipeTileEntityMixin extends BlockEntity implements IRetrieveMode {
    
    @Unique
    private boolean[] pipezretriever$retrievingSides = new boolean[Direction.values().length];
    
    public PipeTileEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    
    @Override
    public boolean isRetrieving(Direction side) {
        return pipezretriever$retrievingSides[side.get3DDataValue()];
    }
    
    @Override
    public void setRetrieving(Direction side, boolean retrieving) {
        pipezretriever$retrievingSides[side.get3DDataValue()] = retrieving;
        setChanged();
    }
    
    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = true)
    private void pipezretriever$loadAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        pipezretriever$retrievingSides = new boolean[Direction.values().length];
        if (tag.contains("PipezRetriever_RetrievingSides", Tag.TAG_LIST)) {
            ListTag retrievingList = tag.getList("PipezRetriever_RetrievingSides", Tag.TAG_BYTE);
            if (retrievingList.size() >= pipezretriever$retrievingSides.length) {
                for (int i = 0; i < pipezretriever$retrievingSides.length; i++) {
                    pipezretriever$retrievingSides[i] = ((ByteTag) retrievingList.get(i)).getAsByte() != 0;
                }
            }
        }
    }
    
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = true)
    private void pipezretriever$saveAdditional(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag retrievingList = new ListTag();
        for (boolean retrieving : pipezretriever$retrievingSides) {
            retrievingList.add(ByteTag.valueOf(retrieving));
        }
        tag.put("PipezRetriever_RetrievingSides", retrievingList);
    }
}
