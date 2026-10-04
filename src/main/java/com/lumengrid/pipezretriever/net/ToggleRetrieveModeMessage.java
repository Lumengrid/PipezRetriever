package com.lumengrid.pipezretriever.net;

import com.lumengrid.pipezretriever.IRetrieveMode;
import com.lumengrid.pipezretriever.PipezRetriever;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.gui.ExtractContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleRetrieveModeMessage(int index) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleRetrieveModeMessage> TYPE = 
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PipezRetriever.MODID, "toggle_retrieve_mode"));

    public static final StreamCodec<FriendlyByteBuf, ToggleRetrieveModeMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ToggleRetrieveModeMessage::index,
            ToggleRetrieveModeMessage::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleRetrieveModeMessage message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof ExtractContainer extractContainer)) {
                return;
            }
            PipeLogicTileEntity pipe = extractContainer.getPipe();
            if (pipe == null) {
                return;
            }
            if (!(pipe instanceof IRetrieveMode retrieveMode)) {
                return;
            }

            boolean currentState = retrieveMode.isRetrieving(extractContainer.getSide());
            retrieveMode.setRetrieving(extractContainer.getSide(), !currentState);

            pipe.syncData(player);
        });
    }
}
