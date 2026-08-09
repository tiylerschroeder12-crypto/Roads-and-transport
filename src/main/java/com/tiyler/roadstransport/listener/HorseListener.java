package com.tiyler.roadstransport.listener;

import com.tiyler.roadstransport.service.HorseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HorseListener implements Listener {
    private final HorseService horses;

    public HorseListener(HorseService horses) {
        this.horses = horses;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        horses.handleInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        horses.handleLeash(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        horses.handleBreed(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        horses.handleTeleport(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        horses.handleInventoryClick(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        horses.handleInventoryDrag(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        horses.handleInventoryClose(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        horses.handleQuit(event);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        horses.handleDeath(event);
    }
}
