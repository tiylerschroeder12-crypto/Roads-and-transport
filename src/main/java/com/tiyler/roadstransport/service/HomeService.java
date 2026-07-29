package com.tiyler.roadstransport.service;

import com.tiyler.roadstransport.bridge.KingdomsBridge;
import com.tiyler.roadstransport.data.DataManager;
import com.tiyler.roadstransport.model.HomeRecord;
import com.tiyler.roadstransport.model.TravelSession;
import com.tiyler.roadstransport.util.Messages;
import com.tiyler.roadstransport.util.WorldUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class HomeService {
    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private final KingdomsBridge kingdoms;
    private final Map<UUID, Map<String, HomeRecord>> homes;
    private final Map<UUID, TravelSession> sessions = new HashMap<>();
    private final int maximum;
    private final int warmupSeconds;
    private final boolean blockedAtNight;
    private final int safeRadius;
    private final long nightStart;
    private final long nightEnd;

    public HomeService(JavaPlugin plugin, DataManager dataManager, KingdomsBridge kingdoms) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.kingdoms = kingdoms;
        homes = dataManager.loadHomes();
        maximum = plugin.getConfig().getInt("homes.maximum", 2);
        warmupSeconds = plugin.getConfig().getInt("homes.warmup-seconds", 10);
        blockedAtNight = plugin.getConfig().getBoolean("homes.blocked-at-night", true);
        safeRadius = plugin.getConfig().getInt("homes.safe-radius", 3);
        nightStart = plugin.getConfig().getLong("waypoints.night-start", 12300);
        nightEnd = plugin.getConfig().getLong("waypoints.night-end", 23850);
    }

    public void save() {
        dataManager.saveHomes(homes);
    }

    public void shutdown() {
        for (TravelSession session : new ArrayList<>(sessions.values())) {
            Bukkit.getScheduler().cancelTask(session.taskId());
        }
        sessions.clear();
        save();
    }

    public void create(Player player, String name) {
        String clean = name.trim();
        if (clean.isBlank() || clean.length() > 32) {
            Messages.error(player, "Home names must be between 1 and 32 characters.");
            return;
        }
        Map<String, HomeRecord> playerHomes = homes.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        String key = normalize(clean);
        if (!playerHomes.containsKey(key) && playerHomes.size() >= maximum) {
            Messages.error(player, "You may have at most " + maximum + " homes.");
            return;
        }
        if (playerHomes.containsKey(key)) {
            Messages.error(player, "You already have a home with that name. Delete it before replacing it.");
            return;
        }
        Location location = player.getLocation();
        Object claim = kingdoms.claimAt(location);
        UUID generatedClaimId = null;
        if (claim != null) {
            if (!kingdoms.isOwner(claim, player.getUniqueId())
                    || (!kingdoms.isPersonal(claim) && !kingdoms.isPolitical(claim))) {
                Messages.error(player, "Homes may only be created in your own personal claim, your own land, or unclaimed land.");
                return;
            }
        } else {
            generatedClaimId = kingdoms.createHomeClaim(player, location, clean);
            if (generatedClaimId == null) {
                Messages.error(player, "The free one-chunk home claim could not be created.");
                return;
            }
        }
        HomeRecord record = new HomeRecord(player.getUniqueId(), clean,
                com.tiyler.roadstransport.model.BlockKey.of(location), generatedClaimId);
        playerHomes.put(key, record);
        save();
        if (generatedClaimId == null) {
            Messages.success(player, "Home Created!");
        } else {
            Messages.success(player, "Home Created! One chunk claimed for saftey");
        }
    }

    public void delete(Player player, String name) {
        Map<String, HomeRecord> playerHomes = homes.get(player.getUniqueId());
        HomeRecord record = playerHomes == null ? null : playerHomes.remove(normalize(name));
        if (record == null) {
            Messages.error(player, "You do not have a home named " + name + ".");
            return;
        }
        if (record.generatedClaimId() != null) kingdoms.removeClaim(record.generatedClaimId());
        if (playerHomes.isEmpty()) homes.remove(player.getUniqueId());
        save();
        Messages.success(player, record.generatedClaimId() == null
                ? "Deleted " + record.name() + "."
                : "Deleted " + record.name() + ", your claim has been removed.");
    }

    public void travel(Player player, String name) {
        Map<String, HomeRecord> playerHomes = homes.get(player.getUniqueId());
        HomeRecord record = playerHomes == null ? null : playerHomes.get(normalize(name));
        if (record == null) {
            Messages.error(player, "You do not have a home named " + name + ".");
            return;
        }
        Location destination = record.location().location();
        if (destination == null) {
            Messages.error(player, "That home's world is unavailable.");
            return;
        }
        String validityError = validityError(player, record, destination);
        if (validityError != null) {
            Messages.error(player, validityError);
            return;
        }
        if (isClosed(destination)) {
            Messages.error(player, "Homes cannot be used until dawn.");
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            Messages.error(player, "You are already preparing to travel home.");
            return;
        }
        Location origin = player.getLocation().clone();
        int[] remaining = {warmupSeconds};
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            TravelSession current = sessions.get(player.getUniqueId());
            if (current == null) return;
            if (!player.isOnline()) {
                sessions.remove(player.getUniqueId());
                Bukkit.getScheduler().cancelTask(current.taskId());
                return;
            }
            if (isClosed(destination)) {
                cancel(player, "Night fell before the home teleport completed.");
                return;
            }
            if (remaining[0] <= 0) {
                complete(player, record);
                return;
            }
            player.sendActionBar(Component.text("Returning to " + record.name() + " in " + remaining[0]
                    + " seconds — Move or sneak to cancel. Teleport will be canceled if damage is taken.", NamedTextColor.AQUA));
            remaining[0]--;
        }, 0L, 20L);
        sessions.put(player.getUniqueId(), new TravelSession(player.getUniqueId(), null, origin, 0,
                System.currentTimeMillis(), taskId, "home"));
    }

    public boolean hasSession(UUID playerId) { return sessions.containsKey(playerId); }

    public Location sessionOrigin(UUID playerId) {
        TravelSession session = sessions.get(playerId);
        return session == null ? null : session.origin();
    }

    public void cancel(Player player, String reason) {
        TravelSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId());
        Messages.error(player, reason);
    }

    public boolean isManagedClaimName(String commandText) {
        String lower = commandText.toLowerCase(Locale.ROOT);
        for (Map<String, HomeRecord> playerHomes : homes.values()) {
            for (HomeRecord home : playerHomes.values()) {
                if (home.generatedClaimId() == null) continue;
                String ownerName = Bukkit.getOfflinePlayer(home.ownerId()).getName();
                if (ownerName == null) continue;
                String claimName = "home - " + ownerName.toLowerCase(Locale.ROOT) + " - " + home.name().toLowerCase(Locale.ROOT);
                if (lower.contains(claimName)) return true;
            }
        }
        return false;
    }

    private void complete(Player player, HomeRecord home) {
        TravelSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId());
        Location center = home.location().location();
        String validityError = center == null ? "That home's world is unavailable." : validityError(player, home, center);
        if (validityError != null) {
            Messages.error(player, validityError);
            return;
        }
        Location safe = WorldUtil.findSafePlayerArrival(center, safeRadius);
        if (safe == null && center != null) {
            Location direct = center.clone().add(0.5, 0, 0.5);
            if (direct.getBlock().isPassable() && direct.clone().add(0, 1, 0).getBlock().isPassable()) safe = direct;
        }
        if (safe == null || !player.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            Messages.error(player, "No safe space could be found at that home.");
            return;
        }
        Messages.success(player, "Returned to " + home.name() + ".");
    }


    private String validityError(Player player, HomeRecord home, Location destination) {
        Object currentClaim = kingdoms.claimAt(destination);
        if (home.generatedClaimId() != null) {
            if (!kingdoms.claimStillOwnedBy(home.generatedClaimId(), player.getUniqueId())) {
                return "That home's free one-chunk claim no longer exists or no longer belongs to you.";
            }
            if (currentClaim == null || !home.generatedClaimId().equals(kingdoms.claimId(currentClaim))) {
                return "That home's protected chunk is no longer valid.";
            }
            return null;
        }
        if (currentClaim != null && !kingdoms.isOwner(currentClaim, player.getUniqueId())) {
            return "That home is now inside land you do not own.";
        }
        return null;
    }

    private boolean isClosed(Location location) {
        return blockedAtNight && WorldUtil.isNight(location.getWorld(), nightStart, nightEnd);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
