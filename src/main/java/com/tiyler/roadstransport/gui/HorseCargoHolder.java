package com.tiyler.roadstransport.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class HorseCargoHolder implements InventoryHolder {
    private final UUID horseId;
    private Inventory inventory;

    public HorseCargoHolder(UUID horseId) {
        this.horseId = horseId;
    }

    public UUID horseId() { return horseId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
