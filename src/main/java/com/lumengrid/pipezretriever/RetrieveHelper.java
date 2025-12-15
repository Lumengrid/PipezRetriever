package com.lumengrid.pipezretriever;

import com.lumengrid.pipezretriever.PipezRetriever;
import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.GasPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for retrieve mode logic.
 * Retrieve mode pulls FROM connected inventories INTO the extracting side's inventory.
 */
public class RetrieveHelper {

    /**
     * Check redstone mode for a side.
     * This replicates the redstone check that shouldWork normally does.
     */
    public static boolean checkRedstone(PipeLogicTileEntity tileEntity, Direction side, PipeType<?, ?> pipeType) {
        UpgradeTileEntity.RedstoneMode redstoneMode = tileEntity.getRedstoneMode(side, pipeType);
        boolean hasPower = tileEntity.isRedstonePowered();
        
        return switch (redstoneMode) {
            case IGNORED -> true;
            case OFF_WHEN_POWERED -> !hasPower;
            case ON_WHEN_POWERED -> hasPower;
            case ALWAYS_OFF -> false;
        };
    }

    /**
     * Check if a connection is a pipe that's also in retrieve mode.
     */
    private static boolean isConnectionInRetrieveMode(Level level, PipeTileEntity.Connection connection) {
        BlockPos pos = connection.getPos();
        Direction side = connection.getDirection();
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IRetrieveMode retrieveMode && be instanceof PipeTileEntity pipe) {
            return pipe.isExtracting(side) && retrieveMode.isRetrieving(side);
        }
        return false;
    }

    // ==================== ITEM RETRIEVE ====================

    public static void retrieveItems(ItemPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) return;
        
        // Destination = the inventory on the extracting side (where we push TO)
        IItemHandler destination = tileEntity.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK,
                extractingConnection.getPos(),
                extractingConnection.getDirection()
        );
        if (destination == null) return;

        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
        UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            retrieveItemsRoundRobin(tileEntity, side, pipeType, connections, destination, rate, filters, filterMode);
        } else {
            retrieveItemsOrdered(tileEntity, connections, destination, rate, filters, filterMode);
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

    private static void retrieveItemsOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections, 
                                              IItemHandler destination, int rate, List<Filter<?, ?>> filters, 
                                              UpgradeTileEntity.FilterMode filterMode) {
        int itemsToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (itemsToTransfer <= 0) break;
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
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

    private static int transferItems(IItemHandler source, IItemHandler destination, int maxAmount,
                                     List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        int transferred = 0;
        
        for (int slot = 0; slot < source.getSlots() && transferred < maxAmount; slot++) {
            ItemStack stack = source.extractItem(slot, maxAmount - transferred, true);
            if (stack.isEmpty()) continue;
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
        
        return filterMode == UpgradeTileEntity.FilterMode.WHITELIST ? matchesAnyFilter : !matchesAnyFilter;
    }

    // ==================== FLUID RETRIEVE ====================

    public static void retrieveFluids(FluidPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) return;
        
        IFluidHandler destination = tileEntity.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                extractingConnection.getPos(),
                extractingConnection.getDirection()
        );
        if (destination == null) return;

        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
        UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
        
        int mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            IFluidHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null) continue;
            
            FluidStack drained = source.drain(mbToTransfer, IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) continue;
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
        
        return filterMode == UpgradeTileEntity.FilterMode.WHITELIST ? matchesAnyFilter : !matchesAnyFilter;
    }

    // ==================== ENERGY RETRIEVE ====================

    public static void retrieveEnergy(EnergyPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Starting for side {}", side);
        
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) {
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] No extracting connection");
            return;
        }
        
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Extracting connection: pos={}, dir={}", 
            extractingConnection.getPos(), extractingConnection.getDirection());
        
        // Destination = where we push energy TO (the machine on the extracting side)
        IEnergyStorage destination = extractingConnection.getEnergyHandler();
        if (destination == null) {
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Destination is null");
            return;
        }
        
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Destination: canReceive={}, stored={}/{}", 
            destination.canReceive(), destination.getEnergyStored(), destination.getMaxEnergyStored());
        
        int testReceive = destination.receiveEnergy(1, true);
        if (!destination.canReceive() && testReceive <= 0) {
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Destination cannot receive");
            return;
        }

        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Found {} connections", connections.size());
        
        if (connections.isEmpty()) {
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] No connections");
            return;
        }
        
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE] Rate={}, Distribution={}", rate, distribution);
        
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            retrieveEnergyRoundRobin(tileEntity, side, pipeType, connections, destination, rate);
        } else {
            retrieveEnergyOrdered(tileEntity, connections, destination, rate);
        }
    }

    private static void retrieveEnergyRoundRobin(PipeLogicTileEntity tileEntity, Direction side, EnergyPipeType pipeType,
                                                  List<PipeTileEntity.Connection> connections, IEnergyStorage destination, int rate) {
        if (connections.isEmpty()) return;
        
        int energyToTransfer = rate;
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        List<IEnergyStorage> sources = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            int index = (i + p) % connections.size();
            PipeTileEntity.Connection connection = connections.get(index);
            
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            IEnergyStorage source = connection.getEnergyHandler();
            if (source != null && source.getEnergyStored() > 0 && 
                (source.canExtract() || source.extractEnergy(1, true) > 0)) {
                sources.add(source);
            }
        }
        
        for (IEnergyStorage source : sources) {
            if (energyToTransfer <= 0) break;
            
            int perSource = Math.max(1, energyToTransfer / sources.size());
            int simulatedExtract = source.extractEnergy(Math.min(perSource, energyToTransfer), true);
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                energyToTransfer -= transferred;
            }
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void retrieveEnergyOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                               IEnergyStorage destination, int rate) {
        int energyToTransfer = rate;
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Starting with rate={}", rate);
        
        for (PipeTileEntity.Connection connection : connections) {
            if (energyToTransfer <= 0) break;
            
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Checking connection pos={}, dir={}", 
                connection.getPos(), connection.getDirection());
            
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) {
                PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Skipping - in retrieve mode");
                continue;
            }
            
            IEnergyStorage source = connection.getEnergyHandler();
            if (source == null) {
                PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Skipping - no energy handler");
                continue;
            }
            
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Source: canExtract={}, stored={}/{}", 
                source.canExtract(), source.getEnergyStored(), source.getMaxEnergyStored());
            
            if (source.getEnergyStored() <= 0) {
                PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Skipping - no energy stored");
                continue;
            }
            
            int testExtract = source.extractEnergy(1, true);
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] testExtract={}", testExtract);
            
            if (!source.canExtract() && testExtract <= 0) {
                PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Skipping - cannot extract");
                continue;
            }
            
            int simulatedExtract = source.extractEnergy(energyToTransfer, true);
            PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] simulatedExtract={}", simulatedExtract);
            
            if (simulatedExtract > 0) {
                int transferred = pushEnergy(source, destination, simulatedExtract);
                PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Transferred {} FE", transferred);
                energyToTransfer -= transferred;
            }
        }
        
        PipezRetriever.LOGGER.info("[ENERGY RETRIEVE ORDERED] Done, remaining={}", energyToTransfer);
    }

    private static int pushEnergy(IEnergyStorage source, IEnergyStorage destination, int maxAmount) {
        PipezRetriever.LOGGER.info("[pushEnergy] maxAmount={}", maxAmount);
        
        int canReceive = destination.receiveEnergy(maxAmount, true);
        PipezRetriever.LOGGER.info("[pushEnergy] canReceive={}", canReceive);
        if (canReceive <= 0) return 0;
        
        int extracted = source.extractEnergy(canReceive, false);
        PipezRetriever.LOGGER.info("[pushEnergy] extracted={}", extracted);
        if (extracted <= 0) return 0;
        
        int received = destination.receiveEnergy(extracted, false);
        PipezRetriever.LOGGER.info("[pushEnergy] received={}", received);
        return received;
    }

    // ==================== GAS/CHEMICAL RETRIEVE ====================

    public static void retrieveGas(GasPipeType pipeType, PipeLogicTileEntity tileEntity, Direction side) {
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) return;
        
        IChemicalHandler destination = extractingConnection.getChemicalHandler();
        if (destination == null) return;

        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        long rate = pipeType.getRate(tileEntity.getUpgrade(side));
        List<Filter<?, ?>> filters = tileEntity.getFilters(side, pipeType);
        UpgradeTileEntity.FilterMode filterMode = tileEntity.getFilterMode(side, pipeType);
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            retrieveGasRoundRobin(tileEntity, side, pipeType, connections, destination, rate, filters, filterMode);
        } else {
            retrieveGasOrdered(tileEntity, connections, destination, rate, filters, filterMode);
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

    private static void retrieveGasOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections, 
                                            IChemicalHandler destination, long rate, List<Filter<?, ?>> filters, 
                                            UpgradeTileEntity.FilterMode filterMode) {
        long mbToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (mbToTransfer <= 0) break;
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            IChemicalHandler source = connection.getChemicalHandler();
            if (source == null) continue;
            
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
            if (!canTransferGas(gasInTank, filters, filterMode)) continue;
            
            ChemicalStack toExtract = new ChemicalStack(gasInTank.getChemical(), maxAmount - transferred);
            ChemicalStack simulatedExtract = source.extractChemical(toExtract, Action.SIMULATE);
            if (simulatedExtract.isEmpty()) continue;
            
            ChemicalStack remainder = destination.insertChemical(simulatedExtract, Action.SIMULATE);
            long toTransfer = simulatedExtract.getAmount() - remainder.getAmount();
            
            if (toTransfer > 0) {
                ChemicalStack extracted = source.extractChemical(new ChemicalStack(gasInTank.getChemical(), toTransfer), Action.EXECUTE);
                destination.insertChemical(extracted, Action.EXECUTE);
                transferred += toTransfer;
            }
        }
        
        return transferred;
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferGas(ChemicalStack stack, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) return true;
        
        for (Filter<?, ?> filter : filters) {
            Filter<?, Chemical> gasFilter = (Filter<?, Chemical>) filter;
            if (gasFilter.isInvert() && matchesGasFilter(gasFilter, stack)) {
                return false;
            }
        }
        
        List<Filter<?, Chemical>> nonInvertedFilters = filters.stream()
                .map(f -> (Filter<?, Chemical>) f)
                .filter(f -> !f.isInvert())
                .collect(Collectors.toList());
        
        if (nonInvertedFilters.isEmpty()) return true;
        
        boolean matchesAny = nonInvertedFilters.stream().anyMatch(f -> matchesGasFilter(f, stack));
        return filterMode == UpgradeTileEntity.FilterMode.WHITELIST ? matchesAny : !matchesAny;
    }

    private static boolean matchesGasFilter(Filter<?, Chemical> filter, ChemicalStack stack) {
        return filter.getTag() == null || filter.getTag().contains(stack.getChemical());
    }

    private static boolean hasAvailable(boolean[] array) {
        for (boolean b : array) {
            if (!b) return true;
        }
        return false;
    }
}
