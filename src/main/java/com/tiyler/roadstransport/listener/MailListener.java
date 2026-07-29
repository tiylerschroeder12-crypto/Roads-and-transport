package com.tiyler.roadstransport.listener;

import com.tiyler.roadstransport.service.MailService;
import com.tiyler.roadstransport.service.WaypointService;
import com.tiyler.roadstransport.util.Messages;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

public final class MailListener implements Listener {
    private final MailService mail;
    private final WaypointService waypoints;

    public MailListener(MailService mail, WaypointService waypoints) {
        this.mail = mail;
        this.waypoints = waypoints;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!mail.isDeliveredCrate(block)) return;
        if (!mail.canOpenDeliveredCrate(event.getPlayer(), block)) {
            event.setCancelled(true);
            Messages.error(event.getPlayer(), "Only the recipient may open this delivered crate.");
            return;
        }
        mail.openedDeliveredCrate(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!mail.isDeliveredCrate(event.getBlock())) return;
        if (!mail.canOpenDeliveredCrate(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            Messages.error(event.getPlayer(), "Only the recipient may break this delivered crate.");
            return;
        }
        mail.brokenDeliveredCrate(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> mail.isDeliveredCrate(block) || waypoints.isWaypointBlock(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> mail.isDeliveredCrate(block) || waypoints.isWaypointBlock(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isProtectedCrate(event.getSource()) || isProtectedCrate(event.getDestination())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> mail.isDeliveredCrate(block) || waypoints.isWaypointBlock(block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> mail.isDeliveredCrate(block) || waypoints.isWaypointBlock(block))) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedCrate(Inventory inventory) {
        if (!(inventory.getHolder() instanceof BlockState state)) return false;
        return mail.isDeliveredCrate(state.getBlock());
    }
}
