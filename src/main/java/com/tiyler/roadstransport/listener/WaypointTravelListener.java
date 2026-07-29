package com.tiyler.roadstransport.listener;

import com.tiyler.roadstransport.service.HomeService;
import com.tiyler.roadstransport.service.WaypointService;
import com.tiyler.roadstransport.util.Messages;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class WaypointTravelListener implements Listener {
    private final WaypointService waypoints;
    private final HomeService homes;

    public WaypointTravelListener(WaypointService waypoints, HomeService homes) {
        this.waypoints = waypoints;
        this.homes = homes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (waypoints.interact(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        waypoints.clickMenu(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!waypoints.isWaypointBlock(block)) return;
        event.setCancelled(true);
        Messages.error(event.getPlayer(), "Use /waypoint remove while looking at this waypoint before breaking the copper block.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        Location origin = waypoints.sessionOrigin(player.getUniqueId());
        if (origin == null) origin = homes.sessionOrigin(player.getUniqueId());
        if (origin == null || !origin.getWorld().equals(event.getTo().getWorld())) return;
        if (origin.distanceSquared(event.getTo()) > 0.04) cancel(player, "Travel cancelled because you moved.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) cancel(player, "Travel cancelled because you took damage.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) cancel(attacker, "Travel cancelled because you attacked.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) cancel(event.getPlayer(), "Travel cancelled.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) cancel(player, "Travel cancelled because you opened an inventory.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), "Travel cancelled because you logged out.");
    }

    private void cancel(Player player, String reason) {
        if (waypoints.hasSession(player.getUniqueId())) waypoints.cancel(player, reason);
        if (homes.hasSession(player.getUniqueId())) homes.cancel(player, reason);
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }
}
