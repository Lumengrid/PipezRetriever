package com.lumengrid.pipezretriever;

import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import de.maxhenkel.pipez.utils.MekanismUtils;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class that handles the retrieve mode logic for all pipe types.
 * This uses the standard NeoForge capability system and respects all pipez configuration:
 * - Redstone mode
 * - Distribution mode (nearest, furthest, round robin, random)
 * - Filter mode (whitelist/blacklist)
 * - Filters
 */
public class RetrieveHelper {

    /**
     * Check if a connection is a pipe that's also in retrieve mode.
     * If so, we should not pull from it (it wants to receive items, not provide them).
     */
    private static boolean isConnectionInRetrieveMode(Level level, PipeTileEntity.Connection connection) {
        BlockPos pos = connection.getPos();
        Direction side = connection.getDirection();
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IRetrieveMode retrieveMode) {
            // Check if this pipe is extracting on the side facing us and is in retrieve mode
            if (be instanceof PipeTileEntity pipe) {
                if (pipe.isExtracting(side) && retrieveMode.pipezretriever$isRetrieving(side)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle item pipe tick with retrieve mode support
     */
    public static void tickItemPipeWithRetrieve(ItemPipeType pipeType, PipeLogicTileEntity tileEntity, IRetrieveMode retrieveMode) {
        for (Direction side : Direction.values()) {
            int speed = pipeType.getSpeed(tileEntity.getUpgrade(side));
            if (tileEntity.getLevel().getGameTime() % speed != 0) {
                continue;
            }
            if (!tileEntity.isExtracting(side)) {
                continue;
            }
            if (!tileEntity.shouldWork(side, pipeType)) {
                continue;
            }
            
            PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
            if (extractingConnection == null) {
                continue;
            }
            
            IItemHandler extractingHandler = tileEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    extractingConnection.getPos(),
                    extractingConnection.getDirection()
            );
            if (extractingHandler == null) {
                continue;
            }

            List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
            int rate = pipeType.getRate(tileEntity.getUpgrade(side));
            List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
            UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
            UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
            
            if (retrieveMode.pipezretriever$isRetrieving(side)) {
                // RETRIEVE MODE: Pull FROM connections INTO the extracting handler
                if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
                    retrieveItemsRoundRobin(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                } else {
                    retrieveItemsOrdered(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                }
            } else {
                // NORMAL MODE: Push FROM extracting handler TO connections
                if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
                    insertItemsRoundRobin(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                } else {
                    insertItemsOrdered(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                }
            }
        }
    }

    private static void retrieveItemsRoundRobin(PipeLogicTileEntity tileEntity, Direction side, ItemPipeType pipeType,
                                                 List<PipeTileEntity.Connection> connections, IItemHandler destination, 
                                                 int rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (connections.isEmpty()) return;
        
        int itemsToTransfer = rate;
        boolean[] sourcesEmpty = new boolean[connections.size()];
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        while (itemsToTransfer > 0 && hasAvailable(sourcesEmpty)) {
            PipeTileEntity.Connection connection = connections.get(p);
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                sourcesEmpty[p] = true;
                p = (p + 1) % connections.size();
                continue;
            }
            
            IItemHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            
            boolean transferred = false;
            if (source != null && !sourcesEmpty[p]) {
                int moved = transferItems(source, destination, 1, filters, filterMode);
                if (moved > 0) {
                    transferred = true;
                    itemsToTransfer -= moved;
                }
            }
            
            if (!transferred) {
                sourcesEmpty[p] = true;
            }
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void retrieveItemsOrdered(PipeLogicTileEntity tileEntity, Direction side, ItemPipeType pipeType,
                                              List<PipeTileEntity.Connection> connections, IItemHandler destination,
                                              int rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        int itemsToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (itemsToTransfer <= 0) break;
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                continue;
            }
            
            IItemHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null) continue;
            
            int moved = transferItems(source, destination, itemsToTransfer, filters, filterMode);
            itemsToTransfer -= moved;
        }
    }

    private static void insertItemsRoundRobin(PipeLogicTileEntity tileEntity, Direction side, ItemPipeType pipeType,
                                               List<PipeTileEntity.Connection> connections, IItemHandler source,
                                               int rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (connections.isEmpty()) return;
        
        int itemsToTransfer = rate;
        boolean[] destsFull = new boolean[connections.size()];
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        while (itemsToTransfer > 0 && hasAvailable(destsFull)) {
            PipeTileEntity.Connection connection = connections.get(p);
            IItemHandler destination = tileEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            
            boolean transferred = false;
            if (destination != null && !destsFull[p]) {
                int moved = transferItems(source, destination, 1, filters, filterMode);
                if (moved > 0) {
                    transferred = true;
                    itemsToTransfer -= moved;
                }
            }
            
            if (!transferred) {
                destsFull[p] = true;
            }
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void insertItemsOrdered(PipeLogicTileEntity tileEntity, Direction side, ItemPipeType pipeType,
                                            List<PipeTileEntity.Connection> connections, IItemHandler source,
                                            int rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        int itemsToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (itemsToTransfer <= 0) break;
            
            IItemHandler destination = tileEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (destination == null) continue;
            
            int moved = transferItems(source, destination, itemsToTransfer, filters, filterMode);
            itemsToTransfer -= moved;
        }
    }

    private static int transferItems(IItemHandler source, IItemHandler destination, int maxAmount,
                                     List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        int transferred = 0;
        
        for (int slot = 0; slot < source.getSlots() && transferred < maxAmount; slot++) {
            ItemStack stack = source.extractItem(slot, maxAmount - transferred, true);
            if (stack.isEmpty()) continue;
            
            // Check filter
            if (!canTransferItem(stack, filters, filterMode)) continue;
            
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, stack, true);
            int toTransfer = stack.getCount() - remainder.getCount();
            
            if (toTransfer > 0) {
                ItemStack extracted = source.extractItem(slot, toTransfer, false);
                ItemHandlerHelper.insertItemStacked(destination, extracted, false);
                transferred += toTransfer;
            }
        }
        
        return transferred;
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferItem(ItemStack stack, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) {
            return filterMode == UpgradeTileEntity.FilterMode.WHITELIST;
        }
        
        boolean matchesAnyFilter = false;
        for (Filter<?, ?> filter : filters) {
            Filter<?, Item> itemFilter = (Filter<?, Item>) filter;
            if (itemFilter.getTag() != null && itemFilter.getTag().contains(stack.getItem())) {
                if (itemFilter.isInvert()) {
                    return filterMode == UpgradeTileEntity.FilterMode.BLACKLIST;
                }
                matchesAnyFilter = true;
            }
        }
        
        if (filterMode == UpgradeTileEntity.FilterMode.WHITELIST) {
            return matchesAnyFilter;
        } else {
            return !matchesAnyFilter;
        }
    }

    /**
     * Handle fluid pipe tick with retrieve mode support
     */
    public static void tickFluidPipeWithRetrieve(FluidPipeType pipeType, PipeLogicTileEntity tileEntity, IRetrieveMode retrieveMode) {
        for (Direction side : Direction.values()) {
            if (!tileEntity.isExtracting(side)) {
                continue;
            }
            if (!tileEntity.shouldWork(side, pipeType)) {
                continue;
            }
            
            PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
            if (extractingConnection == null) {
                continue;
            }
            
            IFluidHandler extractingHandler = tileEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    extractingConnection.getPos(),
                    extractingConnection.getDirection()
            );
            if (extractingHandler == null) {
                continue;
            }

            List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
            int rate = pipeType.getRate(tileEntity.getUpgrade(side));
            List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
            UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
            
            if (retrieveMode.pipezretriever$isRetrieving(side)) {
                retrieveFluids(tileEntity, connections, extractingHandler, rate, filters, filterMode);
            } else {
                insertFluids(tileEntity, connections, extractingHandler, rate, filters, filterMode);
            }
        }
    }

    private static void retrieveFluids(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                       IFluidHandler destination, int rate, List<Filter<?, ?>> filters,
                                       UpgradeTileEntity.FilterMode filterMode) {
        int mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                continue;
            }
            
            IFluidHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null) continue;
            
            FluidStack drained = source.drain(mbToTransfer, IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) continue;
            
            // Check filter
            if (!canTransferFluid(drained, filters, filterMode)) continue;
            
            int filled = destination.fill(drained, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                FluidStack toDrain = source.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                destination.fill(toDrain, IFluidHandler.FluidAction.EXECUTE);
                mbToTransfer -= filled;
            }
        }
    }

    private static void insertFluids(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                    IFluidHandler source, int rate, List<Filter<?, ?>> filters,
                                    UpgradeTileEntity.FilterMode filterMode) {
        int mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            
            IFluidHandler destination = tileEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (destination == null) continue;
            
            FluidStack drained = source.drain(mbToTransfer, IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) continue;
            
            // Check filter
            if (!canTransferFluid(drained, filters, filterMode)) continue;
            
            int filled = destination.fill(drained, IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                FluidStack toDrain = source.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                destination.fill(toDrain, IFluidHandler.FluidAction.EXECUTE);
                mbToTransfer -= filled;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferFluid(FluidStack stack, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) {
            return filterMode == UpgradeTileEntity.FilterMode.WHITELIST;
        }
        
        boolean matchesAnyFilter = false;
        for (Filter<?, ?> filter : filters) {
            Filter<?, Fluid> fluidFilter = (Filter<?, Fluid>) filter;
            if (fluidFilter.getTag() != null && fluidFilter.getTag().contains(stack.getFluid())) {
                if (fluidFilter.isInvert()) {
                    return filterMode == UpgradeTileEntity.FilterMode.BLACKLIST;
                }
                matchesAnyFilter = true;
            }
        }
        
        if (filterMode == UpgradeTileEntity.FilterMode.WHITELIST) {
            return matchesAnyFilter;
        } else {
            return !matchesAnyFilter;
        }
    }

    /**
     * Handle energy pipe pull in NORMAL mode.
     * Normal mode: pull energy FROM source machine INTO connected machines (destinations).
     * Uses IEnergyStorage via Connection.getEnergyHandler() for consistency with pipez.
     */
    public static void pullEnergyNormal(EnergyPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        if (!tileEntity.isExtracting(side)) {
            return;
        }
        if (!tileEntity.shouldWork(side, pipeType)) {
            return;
        }
        
        // Get the "extracting" connection - this is the source we pull FROM
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) {
            return;
        }
        
        // This is the source we want to pull energy FROM
        IEnergyStorage source = extractingConnection.getEnergyHandler();
        if (source == null || !source.canExtract()) {
            return;
        }

        // Get all other connections - these are destinations we want to push TO
        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        if (connections.isEmpty()) {
            return;
        }
        
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        // NORMAL MODE: Pull energy FROM source INTO connections (destinations)
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            pushEnergyRoundRobin(tileEntity, side, pipeType, connections, source, rate);
        } else {
            pushEnergyOrdered(tileEntity, connections, source, rate);
        }
    }
    
    /**
     * Handle energy pipe pull with retrieve mode.
     * In retrieve mode: pull energy FROM connected machines INTO the source machine.
     * Uses IEnergyStorage via Connection.getEnergyHandler() for consistency with pipez.
     */
    public static void pullEnergyWithRetrieve(EnergyPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        PipezRetriever.LOGGER.info("[Energy RETRIEVE] pullEnergyWithRetrieve called for side {}", side);
        
        if (!tileEntity.isExtracting(side)) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] Side {} is not extracting, returning", side);
            return;
        }
        if (!tileEntity.shouldWork(side, pipeType)) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] Side {} shouldWork=false (redstone?), returning", side);
            return;
        }
        
        // Get the "extracting" connection - this is where we want to PUSH energy TO in retrieve mode
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] No extracting connection for side {}", side);
            return;
        }
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE] Extracting connection: pos={}, dir={}", 
            extractingConnection.getPos(), extractingConnection.getDirection());
        
        // This is the destination where we want to push energy (the machine on the extracting side)
        IEnergyStorage destination = extractingConnection.getEnergyHandler();
        if (destination == null) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] Destination energy handler is null");
            return;
        }
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE] Destination: canReceive={}, stored={}/{}", 
            destination.canReceive(), destination.getEnergyStored(), destination.getMaxEnergyStored());
        
        // Check if destination can receive - be lenient, try receiveEnergy simulation
        // Some energy storages report canReceive=false but still accept energy
        int testReceive = destination.receiveEnergy(1, true);
        if (!destination.canReceive() && testReceive <= 0) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] Destination cannot receive (canReceive={}, testReceive={})", 
                destination.canReceive(), testReceive);
            return;
        }

        // Get all other connections - these are sources we want to pull FROM
        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        if (connections.isEmpty()) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE] No connections to pull from");
            return;
        }
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE] Found {} connections to check", connections.size());
        
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE] Rate={}, Distribution={}", rate, distribution);
        
        // RETRIEVE MODE: Pull energy FROM connections INTO the destination
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            retrieveEnergyRoundRobin(tileEntity, side, pipeType, connections, destination, rate);
        } else {
            retrieveEnergyOrdered(tileEntity, connections, destination, rate);
        }
    }

    /**
     * Push energy from source to destinations with round robin distribution (NORMAL mode)
     * Uses EnergyUtils.pushEnergy() for atomic transfers like pipez does.
     */
    private static void pushEnergyRoundRobin(PipeLogicTileEntity tileEntity, Direction side, EnergyPipeType pipeType,
                                              List<PipeTileEntity.Connection> connections, IEnergyStorage source, int rate) {
        if (connections.isEmpty()) {
            return;
        }
        
        int completeAmount = rate;
        int energyToTransfer = completeAmount;
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        // First, collect valid destinations (same approach as pipez)
        java.util.List<IEnergyStorage> destinations = new java.util.ArrayList<>(connections.size());
        for (int i = 0; i < connections.size(); i++) {
            int index = (i + p) % connections.size();
            PipeTileEntity.Connection connection = connections.get(index);
            IEnergyStorage destination = connection.getEnergyHandler();
            if (destination != null && destination.canReceive() && destination.receiveEnergy(1, true) >= 1) {
                destinations.add(destination);
            }
        }
        
        // Then transfer to each destination
        for (IEnergyStorage destination : destinations) {
            int simulatedExtract = source.extractEnergy(Math.min(Math.max(completeAmount / destinations.size(), 1), energyToTransfer), true);
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                if (transferred > 0) {
                    energyToTransfer -= transferred;
                }
            }
            
            p = (p + 1) % connections.size();
            
            if (energyToTransfer <= 0) {
                break;
            }
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    /**
     * Push energy from source to destinations in order (NORMAL mode)
     * Uses EnergyUtils.pushEnergy() for atomic transfers like pipez does.
     */
    private static void pushEnergyOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                           IEnergyStorage source, int rate) {
        int energyToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (energyToTransfer <= 0) break;
            
            IEnergyStorage destination = connection.getEnergyHandler();
            if (destination == null || !destination.canReceive()) {
                continue;
            }
            
            int simulatedExtract = source.extractEnergy(energyToTransfer, true);
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                energyToTransfer -= transferred;
            }
        }
    }

    /**
     * Pull energy from connections into destination with round robin distribution (RETRIEVE mode)
     * Uses EnergyUtils.pushEnergy() for atomic transfers like pipez does.
     */
    private static void retrieveEnergyRoundRobin(PipeLogicTileEntity tileEntity, Direction side, EnergyPipeType pipeType,
                                                  List<PipeTileEntity.Connection> connections, IEnergyStorage destination, int rate) {
        if (connections.isEmpty()) {
            PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] No connections");
            return;
        }
        
        int completeAmount = rate;
        int energyToTransfer = completeAmount;
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Starting with rate={}, {} connections", rate, connections.size());
        
        // First, collect valid sources (excluding pipes in retrieve mode)
        java.util.List<IEnergyStorage> sources = new java.util.ArrayList<>(connections.size());
        for (int i = 0; i < connections.size(); i++) {
            int index = (i + p) % connections.size();
            PipeTileEntity.Connection connection = connections.get(index);
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Connection {} is in retrieve mode, skipping", connection.getPos());
                continue;
            }
            
            IEnergyStorage source = connection.getEnergyHandler();
            if (source == null) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Connection {} has no energy handler", connection.getPos());
                continue;
            }
            
            // Be lenient: try extraction simulation even if canExtract() returns false
            // Some energy storages report canExtract=false but still allow extraction
            if (source.getEnergyStored() > 0 && (source.canExtract() || source.extractEnergy(1, true) > 0)) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Adding source at {}: canExtract={}, stored={}", 
                    connection.getPos(), source.canExtract(), source.getEnergyStored());
                sources.add(source);
            } else {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Source at {} cannot extract: canExtract={}, stored={}", 
                    connection.getPos(), source.canExtract(), source.getEnergyStored());
            }
        }
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Found {} valid sources", sources.size());
        
        // Then transfer from each source
        for (IEnergyStorage source : sources) {
            int simulatedExtract = source.extractEnergy(Math.min(Math.max(completeAmount / sources.size(), 1), energyToTransfer), true);
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                if (transferred > 0) {
                    energyToTransfer -= transferred;
                    PipezRetriever.LOGGER.info("[Energy RETRIEVE RoundRobin] Transferred {}, remaining={}", transferred, energyToTransfer);
                }
            }
            
            p = (p + 1) % connections.size();
            
            if (energyToTransfer <= 0) {
                break;
            }
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    /**
     * Pull energy from connections into destination in order (RETRIEVE mode)
     * Uses EnergyUtils.pushEnergy() for atomic transfers like pipez does.
     */
    private static void retrieveEnergyOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                               IEnergyStorage destination, int rate) {
        int energyToTransfer = rate;
        PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Starting with rate={}", rate);
        
        for (PipeTileEntity.Connection connection : connections) {
            if (energyToTransfer <= 0) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Energy quota exhausted");
                break;
            }
            
            PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Checking connection: pos={}, dir={}", 
                connection.getPos(), connection.getDirection());
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Skipping - connection is also in retrieve mode");
                continue;
            }
            
            IEnergyStorage source = connection.getEnergyHandler();
            if (source == null) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Skipping - no energy handler");
                continue;
            }
            
            PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Source: canExtract={}, stored={}/{}", 
                source.canExtract(), source.getEnergyStored(), source.getMaxEnergyStored());
            
            if (source.getEnergyStored() <= 0) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Skipping - no energy stored");
                continue;
            }
            
            // Be lenient: try extraction even if canExtract() returns false
            int testExtract = source.extractEnergy(1, true);
            if (!source.canExtract() && testExtract <= 0) {
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Skipping - cannot extract (canExtract={}, testExtract={})", 
                    source.canExtract(), testExtract);
                continue;
            }
            
            int simulatedExtract = source.extractEnergy(energyToTransfer, true);
            PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Simulated extract: {}", simulatedExtract);
            
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Actually transferred: {}", transferred);
                energyToTransfer -= transferred;
            }
        }
        
        PipezRetriever.LOGGER.info("[Energy RETRIEVE Ordered] Finished, remaining quota: {}", energyToTransfer);
    }

    /**
     * Push energy from source to destination atomically.
     * Extracts from source and inserts into destination, returning the amount actually transferred.
     * This replicates the behavior of de.maxhenkel.corelib.energy.EnergyUtils.pushEnergy().
     */
    private static int pushEnergy(IEnergyStorage source, IEnergyStorage destination, int maxAmount) {
        PipezRetriever.LOGGER.info("[Energy pushEnergy] Attempting to push {} energy", maxAmount);
        
        int canReceive = destination.receiveEnergy(maxAmount, true);
        PipezRetriever.LOGGER.info("[Energy pushEnergy] Destination can receive: {}", canReceive);
        
        if (canReceive <= 0) {
            PipezRetriever.LOGGER.info("[Energy pushEnergy] Destination cannot receive any energy");
            return 0;
        }
        
        int extracted = source.extractEnergy(canReceive, false);
        PipezRetriever.LOGGER.info("[Energy pushEnergy] Actually extracted from source: {}", extracted);
        
        if (extracted <= 0) {
            PipezRetriever.LOGGER.info("[Energy pushEnergy] Could not extract any energy from source");
            return 0;
        }
        
        int received = destination.receiveEnergy(extracted, false);
        PipezRetriever.LOGGER.info("[Energy pushEnergy] Destination actually received: {}", received);
        
        return received;
    }

    private static boolean hasAvailable(boolean[] array) {
        for (boolean b : array) {
            if (!b) return true;
        }
        return false;
    }

    // ==================== GAS/CHEMICAL PIPE HANDLING ====================

    /**
     * Handle gas pipe tick with retrieve mode support.
     * This handles Mekanism chemicals.
     */
    public static void tickGasPipeWithRetrieve(GasPipeType pipeType, PipeLogicTileEntity tileEntity, IRetrieveMode retrieveMode) {
        if (!MekanismUtils.isMekanismInstalled()) {
            return;
        }
        
        for (Direction side : Direction.values()) {
            if (!tileEntity.isExtracting(side)) {
                continue;
            }
            if (!tileEntity.shouldWork(side, pipeType)) {
                continue;
            }
            
            PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
            if (extractingConnection == null) {
                continue;
            }
            
            IChemicalHandler extractingHandler = extractingConnection.getChemicalHandler();
            if (extractingHandler == null) {
                continue;
            }

            List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
            long rate = pipeType.getRate(tileEntity.getUpgrade(side));
            List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
            UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
            UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
            
            if (retrieveMode.pipezretriever$isRetrieving(side)) {
                // RETRIEVE MODE: Pull FROM connections INTO the extracting handler
                if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
                    retrieveGasRoundRobin(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                } else {
                    retrieveGasOrdered(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                }
            } else {
                // NORMAL MODE: Push FROM extracting handler TO connections
                if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
                    insertGasRoundRobin(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                } else {
                    insertGasOrdered(tileEntity, side, pipeType, connections, extractingHandler, rate, filters, filterMode);
                }
            }
        }
    }

    private static void retrieveGasRoundRobin(PipeLogicTileEntity tileEntity, Direction side, GasPipeType pipeType,
                                               List<PipeTileEntity.Connection> connections, IChemicalHandler destination,
                                               long rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (connections.isEmpty()) return;
        
        long mbToTransfer = rate;
        boolean[] sourcesEmpty = new boolean[connections.size()];
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        while (mbToTransfer > 0 && hasAvailable(sourcesEmpty)) {
            PipeTileEntity.Connection connection = connections.get(p);
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                sourcesEmpty[p] = true;
                p = (p + 1) % connections.size();
                continue;
            }
            
            IChemicalHandler source = connection.getChemicalHandler();
            boolean transferred = false;
            
            if (source != null && !sourcesEmpty[p]) {
                long moved = transferGas(source, destination, mbToTransfer, filters, filterMode);
                if (moved > 0) {
                    transferred = true;
                    mbToTransfer -= moved;
                }
            }
            
            if (!transferred) {
                sourcesEmpty[p] = true;
            }
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void retrieveGasOrdered(PipeLogicTileEntity tileEntity, Direction side, GasPipeType pipeType,
                                            List<PipeTileEntity.Connection> connections, IChemicalHandler destination,
                                            long rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        long mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            
            // Skip connections that are pipes in retrieve mode
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                continue;
            }
            
            IChemicalHandler source = connection.getChemicalHandler();
            if (source == null) continue;
            
            long moved = transferGas(source, destination, mbToTransfer, filters, filterMode);
            mbToTransfer -= moved;
        }
    }

    private static void insertGasRoundRobin(PipeLogicTileEntity tileEntity, Direction side, GasPipeType pipeType,
                                             List<PipeTileEntity.Connection> connections, IChemicalHandler source,
                                             long rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (connections.isEmpty()) return;
        
        long mbToTransfer = rate;
        boolean[] destsFull = new boolean[connections.size()];
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        while (mbToTransfer > 0 && hasAvailable(destsFull)) {
            PipeTileEntity.Connection connection = connections.get(p);
            IChemicalHandler destination = connection.getChemicalHandler();
            
            boolean transferred = false;
            if (destination != null && !destsFull[p]) {
                long moved = transferGas(source, destination, mbToTransfer, filters, filterMode);
                if (moved > 0) {
                    transferred = true;
                    mbToTransfer -= moved;
                }
            }
            
            if (!transferred) {
                destsFull[p] = true;
            }
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void insertGasOrdered(PipeLogicTileEntity tileEntity, Direction side, GasPipeType pipeType,
                                          List<PipeTileEntity.Connection> connections, IChemicalHandler source,
                                          long rate, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        long mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            
            IChemicalHandler destination = connection.getChemicalHandler();
            if (destination == null) continue;
            
            long moved = transferGas(source, destination, mbToTransfer, filters, filterMode);
            mbToTransfer -= moved;
        }
    }

    private static long transferGas(IChemicalHandler source, IChemicalHandler destination, long maxAmount,
                                    List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        long transferred = 0;
        
        for (int tank = 0; tank < source.getChemicalTanks() && transferred < maxAmount; tank++) {
            ChemicalStack gasInTank = source.getChemicalInTank(tank);
            if (gasInTank.isEmpty()) continue;
            
            // Check filter
            if (!canTransferGas(gasInTank, filters, filterMode)) continue;
            
            // Simulate extraction
            ChemicalStack toExtract = new ChemicalStack(gasInTank.getChemical(), maxAmount - transferred);
            ChemicalStack simulatedExtract = source.extractChemical(toExtract, Action.SIMULATE);
            if (simulatedExtract.isEmpty()) continue;
            
            // Simulate insertion
            ChemicalStack remainder = destination.insertChemical(simulatedExtract, Action.SIMULATE);
            long toTransfer = simulatedExtract.getAmount() - remainder.getAmount();
            
            if (toTransfer > 0) {
                // Actually perform the transfer
                ChemicalStack extracted = source.extractChemical(new ChemicalStack(gasInTank.getChemical(), toTransfer), Action.EXECUTE);
                destination.insertChemical(extracted, Action.EXECUTE);
                transferred += toTransfer;
            }
        }
        
        return transferred;
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferGas(ChemicalStack stack, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) {
            // No filters - in whitelist mode allow everything, in blacklist mode allow everything
            return true;
        }
        
        // Check inverted filters first (these always block if matched)
        for (Filter<?, ?> filter : filters) {
            Filter<?, Chemical> gasFilter = (Filter<?, Chemical>) filter;
            if (gasFilter.isInvert() && matchesGasFilter(gasFilter, stack)) {
                return false;
            }
        }
        
        // Get non-inverted filters
        List<Filter<?, Chemical>> nonInvertedFilters = filters.stream()
                .map(f -> (Filter<?, Chemical>) f)
                .filter(f -> !f.isInvert())
                .collect(Collectors.toList());
        
        if (nonInvertedFilters.isEmpty()) {
            // Only inverted filters exist, and we passed them
            return true;
        }
        
        // Check if matches any non-inverted filter
        boolean matchesAny = nonInvertedFilters.stream().anyMatch(f -> matchesGasFilter(f, stack));
        
        if (filterMode == UpgradeTileEntity.FilterMode.WHITELIST) {
            return matchesAny;
        } else {
            return !matchesAny;
        }
    }

    private static boolean matchesGasFilter(Filter<?, Chemical> filter, ChemicalStack stack) {
        return filter.getTag() == null || filter.getTag().contains(stack.getChemical());
    }
}
