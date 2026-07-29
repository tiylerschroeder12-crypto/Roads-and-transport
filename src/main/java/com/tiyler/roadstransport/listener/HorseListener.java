package com.tiyler.roadstransport.listener;

import com.tiyler.roadstransport.service.HorseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class HorseListener implements Listener {
    private final HorseService horses;

    public HorseListener(HorseService horses) {
        this.horses = horses;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        horses.handleInteract(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        horses.handleBreed(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        horses.handleTeleport(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        horses.handleInventoryClose(event);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        horses.handleDeath(event);
    }
}
