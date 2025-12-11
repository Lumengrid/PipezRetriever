package com.lumengrid.pipezretriever;

import com.lumengrid.pipezretriever.net.ToggleRetrieveModeMessage;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(PipezRetriever.MODID)
public class PipezRetriever {
    public static final String MODID = "pipezretriever";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PipezRetriever(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Pipez Retriever initializing...");
        
        // Register network handlers
        modEventBus.addListener(this::registerPayloads);
    }
    
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MODID).versioned("1.0.0");
        
        registrar.playToServer(
                ToggleRetrieveModeMessage.TYPE,
                ToggleRetrieveModeMessage.STREAM_CODEC,
                ToggleRetrieveModeMessage::handle
        );
        
        LOGGER.info("Pipez Retriever network messages registered");
    }
}
