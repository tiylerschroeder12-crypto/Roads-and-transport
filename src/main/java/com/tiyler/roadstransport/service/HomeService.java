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
    private final int safeRadius;

    public HomeService(JavaPlugin plugin, DataManager dataManager, KingdomsBridge kingdoms) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.kingdoms = kingdoms;
        homes = dataManager.loadHomes();
        maximum = plugin.getConfig().getInt("homes.maximum", 4);
        warmupSeconds = plugin.getConfig().getInt("homes.warmup-seconds", 10);
        safeRadius = plugin.getConfig().getInt("homes.safe-radius", 3);
        migrateLegacyHomeClaims();
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

    public List<String> names(UUID playerId) {
        Map<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null || playerHomes.isEmpty()) return List.of();
        return playerHomes.values().stream()
                .map(HomeRecord::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void list(Player player) {
        list(player, false);
    }

    public void listForDeletion(Player player) {
        list(player, true);
    }

    private void list(Player player, boolean deleting) {
        List<HomeRecord> records = records(player.getUniqueId());
        if (records.isEmpty()) {
            Messages.info(player, "You do not have any homes.");
            return;
        }

        String command = deleting ? "/delhome " : "/home ";
        StringBuilder message = new StringBuilder("Your homes (")
                .append(records.size()).append('/').append(maximum).append("):");
        for (HomeRecord home : records) {
            long visits = home.visitCount();
            message.append("\n")
                    .append(command).append(home.name())
                    .append(" [visited ").append(visits).append(visits == 1 ? " time]" : " times]");
        }
        if (!deleting) {
            message.append("\nUse /homelist to show this list again.");
        }

        player.sendMessage(Component.text(message.toString(), NamedTextColor.GRAY));
    }

    private List<HomeRecord> records(UUID playerId) {
        Map<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null || playerHomes.isEmpty()) return List.of();
        return playerHomes.values().stream()
                .sorted(Comparator.comparing(HomeRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
        HomeRecord record = new HomeRecord(player.getUniqueId(), clean,
                com.tiyler.roadstransport.model.BlockKey.of(location), null, 0L);
        playerHomes.put(key, record);
        save();
        Messages.success(player, "Home Created!");
    }

    public void delete(Player player, String name) {
        Map<String, HomeRecord> playerHomes = homes.get(player.getUniqueId());
        HomeRecord record = playerHomes == null ? null : playerHomes.remove(normalize(name));
        if (record == null) {
            Messages.error(player, "You do not have a home named " + name + ".");
            return;
        }
        if (playerHomes.isEmpty()) homes.remove(player.getUniqueId());
        save();
        Messages.success(player, "Deleted " + record.name() + ".");
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

    /**
     * Compatibility shim for 0.1.5-era HomeClaimListener source files that may remain
     * in repositories updated by overlaying the 0.1.6 source tree. Homes are no longer
     * managed claims, so this always returns false and keeps that obsolete listener inert.
     *
     * @deprecated Homes have been pure teleport anchors since 0.1.6-alpha.
     */
    @Deprecated(forRemoval = true)
    public boolean isManagedClaimName(String ignoredCommand) {
        return false;
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

    private void complete(Player player, HomeRecord home) {
        TravelSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId());
        Location center = home.location().location();
        if (center == null) {
            Messages.error(player, "That home's world is unavailable.");
            return;
        }
        Location safe = directSafeArrival(center);
        if (safe == null) safe = WorldUtil.findSafePlayerArrival(center, safeRadius);
        if (safe == null || !player.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            Messages.error(player, "No safe space could be found at that home.");
            return;
        }

        Map<String, HomeRecord> playerHomes = homes.get(player.getUniqueId());
        if (playerHomes != null) {
            String key = normalize(home.name());
            HomeRecord current = playerHomes.get(key);
            if (current != null) {
                playerHomes.put(key, current.withVisitCount(current.visitCount() + 1L));
                save();
            }
        }
        Messages.success(player, "Returned to " + home.name() + ".");
    }

    private Location directSafeArrival(Location center) {
        Location direct = center.clone().add(0.5, 0, 0.5);
        if (!direct.getBlock().isPassable() || !direct.clone().add(0, 1, 0).getBlock().isPassable()) return null;
        org.bukkit.block.Block floor = direct.clone().add(0, -1, 0).getBlock();
        if (!floor.getType().isSolid()) return null;
        return direct;
    }

    /**
     * 0.1.5 and older could create a synthetic KingdomsAndCurrency claim for a home
     * placed in unclaimed land. Homes are teleport anchors beginning in 0.1.6, so those
     * legacy claims are removed once and the home itself is retained. Failed removals keep
     * their legacy id in homes.yml so another server start can retry instead of orphaning it.
     */
    private void migrateLegacyHomeClaims() {
        int cleaned = 0;
        int failed = 0;
        boolean changed = false;
        for (Map.Entry<UUID, Map<String, HomeRecord>> ownerEntry : homes.entrySet()) {
            Map<String, HomeRecord> playerHomes = ownerEntry.getValue();
            for (Map.Entry<String, HomeRecord> homeEntry : new ArrayList<>(playerHomes.entrySet())) {
                HomeRecord home = homeEntry.getValue();
                UUID claimId = home.legacyGeneratedClaimId();
                if (claimId == null) continue;
                if (kingdoms.removeClaim(claimId)) {
                    playerHomes.put(homeEntry.getKey(), home.withoutLegacyClaim());
                    cleaned++;
                    changed = true;
                } else {
                    plugin.getLogger().warning("Could not remove legacy generated home claim " + claimId
                            + " for " + home.name() + "; it will be retried on the next server start.");
                    failed++;
                }
            }
        }
        if (changed) save();
        if (cleaned > 0 || failed > 0) {
            plugin.getLogger().info("Home-anchor migration: cleaned " + cleaned + " legacy generated claim references, "
                    + failed + " pending retry.");
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
