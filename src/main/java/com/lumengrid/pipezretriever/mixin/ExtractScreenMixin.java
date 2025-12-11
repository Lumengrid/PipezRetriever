package com.lumengrid.pipezretriever.mixin;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.net.ToggleRetrieveModeMessage;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.gui.ExtractContainer;
import de.maxhenkel.pipez.gui.ExtractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtractScreen.class)
public abstract class ExtractScreenMixin extends AbstractContainerScreen<ExtractContainer> {
    
    @Unique
    private Button pipezretriever$retrieveButton;
    
    // Constructor required by mixin extending a class
    protected ExtractScreenMixin() {
        super(null, null, null);
    }
    
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void pipezretriever$init(CallbackInfo ci) {
        ExtractContainer container = this.menu;
        PipeLogicTileEntity pipe = container.getPipe();
        
        // Create retrieve mode toggle button at top-left of the GUI
        pipezretriever$retrieveButton = Button.builder(
                pipezretriever$getRetrieveButtonText(pipe, container),
                button -> {
                    PacketDistributor.sendToServer(new ToggleRetrieveModeMessage(0));
                }
        ).bounds(this.leftPos + 32, this.topPos - 22, 60, 20).build();
        
        // Set tooltip
        pipezretriever$updateButtonTooltip(pipe, container);
        
        this.addRenderableWidget(pipezretriever$retrieveButton);
        
        // Update button state
        pipezretriever$checkRetrieveButton();
    }
    
    @Inject(method = "containerTick", at = @At("TAIL"), remap = false)
    private void pipezretriever$containerTick(CallbackInfo ci) {
        pipezretriever$checkRetrieveButton();
    }
    
    @Unique
    private void pipezretriever$checkRetrieveButton() {
        if (pipezretriever$retrieveButton == null) {
            return;
        }
        
        ExtractContainer container = this.menu;
        PipeLogicTileEntity pipe = container.getPipe();
        
        // Button is always enabled
        pipezretriever$retrieveButton.active = true;
        
        // Update button text and tooltip based on current state
        pipezretriever$retrieveButton.setMessage(pipezretriever$getRetrieveButtonText(pipe, container));
        pipezretriever$updateButtonTooltip(pipe, container);
    }
    
    @Unique
    private void pipezretriever$updateButtonTooltip(PipeLogicTileEntity pipe, ExtractContainer container) {
        if (pipezretriever$retrieveButton == null) return;
        
        String tooltipText;
        if (pipe instanceof IRetrieveMode retrieveMode && retrieveMode.pipezretriever$isRetrieving(container.getSide())) {
            tooltipText = "Pull FROM connected inventories";
        } else {
            tooltipText = "Push TO connected inventories";
        }
        
        pipezretriever$retrieveButton.setTooltip(Tooltip.create(Component.literal(tooltipText)));
    }
    
    @Unique
    private Component pipezretriever$getRetrieveButtonText(PipeLogicTileEntity pipe, ExtractContainer container) {
        if (pipe instanceof IRetrieveMode retrieveMode) {
            boolean isRetrieving = retrieveMode.pipezretriever$isRetrieving(container.getSide());
            if (isRetrieving) {
                return Component.literal("Retrieve");
            }
        }
        return Component.literal("Extract");
    }
}
