package com.tiyler.roadstransport.listener;

import com.tiyler.roadstransport.service.HomeService;
import com.tiyler.roadstransport.util.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class HomeClaimListener implements Listener {
    private final HomeService homes;

    public HomeClaimListener(HomeService homes) {
        this.homes = homes;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("/claim ") || lower.startsWith("/dissolve "))) return;
        if (!homes.isManagedClaimName(message)) return;
        event.setCancelled(true);
        Messages.error(event.getPlayer(), "That one-chunk claim is tied to a home. Use /delhome <name> instead.");
    }
}
