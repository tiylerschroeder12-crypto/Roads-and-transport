package com.tiyler.roadstransport.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class WaypointMenuHolder implements InventoryHolder {
    private final UUID sourceId;
    private final int page;
    private Inventory inventory;

    public WaypointMenuHolder(UUID sourceId, int page) {
        this.sourceId = sourceId;
        this.page = page;
    }

    public UUID sourceId() { return sourceId; }
    public int page() { return page; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
