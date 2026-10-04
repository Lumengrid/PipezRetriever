package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.net.ToggleRetrieveModeMessage;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.gui.ExtractContainer;
import de.maxhenkel.pipez.gui.ExtractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtractScreen.class)
public abstract class ExtractScreenMixin extends AbstractContainerScreen<ExtractContainer> {
    
    @Unique
    private Button modeButton;
    
    protected ExtractScreenMixin() {
        super(null, null, null);
    }
    
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void pipezretriever$init(CallbackInfo ci) {
        ExtractContainer container = this.menu;
        PipeLogicTileEntity pipe = container.getPipe();

        modeButton = Button.builder(
                getButtonText(pipe, container),
                button -> {
                    ClientPacketDistributor.sendToServer(new ToggleRetrieveModeMessage(0));
                }
        ).bounds(this.leftPos + 32, this.topPos - 22, 60, 20).build();
        
        this.addRenderableWidget(modeButton);
        pipezretriever$checkRetrieveButton();
    }
    
    @Inject(method = "containerTick", at = @At("TAIL"), remap = false)
    private void pipezretriever$containerTick(CallbackInfo ci) {
        pipezretriever$checkRetrieveButton();
    }
    
    @Unique
    private void pipezretriever$checkRetrieveButton() {
        if (modeButton == null) {
            return;
        }
        
        ExtractContainer container = this.menu;
        PipeLogicTileEntity pipe = container.getPipe();
        modeButton.active = true;
        
        modeButton.setMessage(getButtonText(pipe, container));
    }
    
    @Unique
    private Component getButtonText(PipeLogicTileEntity pipe, ExtractContainer container) {
        if (pipe instanceof IRetrieveMode retrieveMode) {
            boolean isRetrieving = retrieveMode.isRetrieving(container.getSide());
            if (isRetrieving) {
                return Component.literal("Retrieve");
            }
        }
        return Component.literal("Extract");
    }
}
