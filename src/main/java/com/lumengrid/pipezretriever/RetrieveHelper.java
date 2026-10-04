package com.lumengrid.pipezretriever;

import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.EnergyPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.FluidPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import de.maxhenkel.pipez.blocks.tileentity.types.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.List;

public class RetrieveHelper {

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
        
        ResourceHandler<ItemResource> destination = tileEntity.getLevel().getCapability(
                Capabilities.Item.BLOCK,
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
                                                 List<PipeTileEntity.Connection> connections, ResourceHandler<ItemResource> destination, 
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
            
            ResourceHandler<ItemResource> source = tileEntity.getLevel().getCapability(
                    Capabilities.Item.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            
            boolean transferred = false;
            if (source != null && !sourcesEmpty[p]) {
                int moved = ResourceHandlerUtil.move(
                        source, 
                        destination, 
                        res -> canTransferItem(res, filters, filterMode), 
                        1, 
                        null
                );
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
                                              ResourceHandler<ItemResource> destination, int rate, List<Filter<?, ?>> filters, 
                                              UpgradeTileEntity.FilterMode filterMode) {
        int itemsToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (itemsToTransfer <= 0) break;
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            ResourceHandler<ItemResource> source = tileEntity.getLevel().getCapability(
                    Capabilities.Item.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null) continue;
            
            int moved = ResourceHandlerUtil.move(
                    source, 
                    destination, 
                    res -> canTransferItem(res, filters, filterMode), 
                    itemsToTransfer, 
                    null
            );
            itemsToTransfer -= moved;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferItem(ItemResource resource, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) {
            return filterMode == UpgradeTileEntity.FilterMode.WHITELIST;
        }
        
        boolean matchesAnyFilter = false;
        for (Filter<?, ?> filter : filters) {
            Filter<?, Item> itemFilter = (Filter<?, Item>) filter;
            if (itemFilter.getTag() != null && itemFilter.getTag().contains(resource.getItem())) {
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
        
        ResourceHandler<FluidResource> destination = tileEntity.getLevel().getCapability(
                Capabilities.Fluid.BLOCK,
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
            
            ResourceHandler<FluidResource> source = tileEntity.getLevel().getCapability(
                    Capabilities.Fluid.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null) continue;
            
            int moved = ResourceHandlerUtil.move(
                    source, 
                    destination, 
                    res -> canTransferFluid(res, filters, filterMode), 
                    mbToTransfer, 
                    null
            );
            mbToTransfer -= moved;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean canTransferFluid(FluidResource resource, List<Filter<?, ?>> filters, UpgradeTileEntity.FilterMode filterMode) {
        if (filters.isEmpty()) {
            return filterMode == UpgradeTileEntity.FilterMode.WHITELIST;
        }
        
        boolean matchesAnyFilter = false;
        for (Filter<?, ?> filter : filters) {
            Filter<?, Fluid> fluidFilter = (Filter<?, Fluid>) filter;
            if (fluidFilter.getTag() != null && fluidFilter.getTag().contains(resource.getFluid())) {
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
        PipeTileEntity.Connection extractingConnection = tileEntity.getExtractingConnection(side);
        if (extractingConnection == null) return;

        EnergyHandler destination = tileEntity.getLevel().getCapability(
                Capabilities.Energy.BLOCK,
                extractingConnection.getPos(),
                extractingConnection.getDirection()
        );
        if (destination == null || EnergyHandlerUtil.isFull(destination)) return;

        List<PipeTileEntity.Connection> connections = tileEntity.getSortedConnections(side, pipeType);
        if (connections.isEmpty()) return;
        
        int rate = pipeType.getRate(tileEntity.getUpgrade(side));
        UpgradeTileEntity.Distribution distribution = tileEntity.getDistribution(side, pipeType);
        
        if (distribution == UpgradeTileEntity.Distribution.ROUND_ROBIN) {
            retrieveEnergyRoundRobin(tileEntity, side, pipeType, connections, destination, rate);
        } else {
            retrieveEnergyOrdered(tileEntity, connections, destination, rate);
        }
    }

    private static void retrieveEnergyRoundRobin(PipeLogicTileEntity tileEntity, Direction side, EnergyPipeType pipeType,
                                                  List<PipeTileEntity.Connection> connections, EnergyHandler destination, int rate) {
        if (connections.isEmpty()) return;
        
        int energyToTransfer = rate;
        int p = tileEntity.getRoundRobinIndex(side, pipeType) % connections.size();
        
        List<EnergyHandler> sources = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            int index = (i + p) % connections.size();
            PipeTileEntity.Connection connection = connections.get(index);
            
            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            EnergyHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.Energy.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source != null && source.getAmountAsLong() > 0) {
                sources.add(source);
            }
        }
        
        for (EnergyHandler source : sources) {
            if (energyToTransfer <= 0 || EnergyHandlerUtil.isFull(destination)) break;
            
            int perSource = Math.max(1, energyToTransfer / sources.size());
            int moved = EnergyHandlerUtil.move(source, destination, perSource, null);
            energyToTransfer -= moved;
            p = (p + 1) % connections.size();
        }
        
        tileEntity.setRoundRobinIndex(side, pipeType, p);
    }

    private static void retrieveEnergyOrdered(PipeLogicTileEntity tileEntity, List<PipeTileEntity.Connection> connections,
                                               EnergyHandler destination, int rate) {
        int energyToTransfer = rate;
        
        for (PipeTileEntity.Connection connection : connections) {
            if (energyToTransfer <= 0 || EnergyHandlerUtil.isFull(destination)) break;

            if (isConnectionInRetrieveMode(tileEntity.getLevel(), connection)) continue;
            
            EnergyHandler source = tileEntity.getLevel().getCapability(
                    Capabilities.Energy.BLOCK,
                    connection.getPos(),
                    connection.getDirection()
            );
            if (source == null || source.getAmountAsLong() <= 0) continue;
            
            int moved = EnergyHandlerUtil.move(source, destination, energyToTransfer, null);
            energyToTransfer -= moved;
        }
    }

    private static boolean hasAvailable(boolean[] array) {
        for (boolean b : array) {
            if (!b) return true;
        }
        return false;
    }
}
