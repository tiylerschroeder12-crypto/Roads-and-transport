package com.tiyler.roadstransport.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class HorseCargoHolder implements InventoryHolder {
    private final UUID rootHorseId;
    private final UUID sourceHorseId;
    private final int pageIndex;
    private final int totalPages;
    private final int sourceOffset;
    private final int storageSlots;
    private Inventory inventory;

    public HorseCargoHolder(UUID rootHorseId, UUID sourceHorseId, int pageIndex, int totalPages,
                            int sourceOffset, int storageSlots) {
        this.rootHorseId = rootHorseId;
        this.sourceHorseId = sourceHorseId;
        this.pageIndex = pageIndex;
        this.totalPages = totalPages;
        this.sourceOffset = sourceOffset;
        this.storageSlots = storageSlots;
    }

    public UUID horseId() { return rootHorseId; }
    public UUID rootHorseId() { return rootHorseId; }
    public UUID sourceHorseId() { return sourceHorseId; }
    public int pageIndex() { return pageIndex; }
    public int totalPages() { return totalPages; }
    public int sourceOffset() { return sourceOffset; }
    public int storageSlots() { return storageSlots; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
