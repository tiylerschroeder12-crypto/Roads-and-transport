package com.tiyler.roadstransport.service;

import com.tiyler.roadstransport.bridge.KingdomsBridge;
import com.tiyler.roadstransport.data.DataManager;
import com.tiyler.roadstransport.gui.WaypointMenuHolder;
import com.tiyler.roadstransport.model.*;
import com.tiyler.roadstransport.util.Messages;
import com.tiyler.roadstransport.util.WorldUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class WaypointService {
    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private final KingdomsBridge kingdoms;
    private final Map<UUID, WaypointRecord> waypoints;
    private final Map<BlockKey, UUID> byBlock = new HashMap<>();
    private final Map<UUID, TravelSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> displayByWaypoint = new HashMap<>();
    private final NamespacedKey destinationKey;
    private final NamespacedKey displayKey;
    private final NamespacedKey displayWaypointKey;
    private final int creationCost;
    private final int publicFare;
    private final int personalFare;
    private final int accessCost;
    private final int warmupSeconds;
    private final int safeRadius;
    private final double labelHeight;
    private final boolean blockedAtNight;
    private final long nightStart;
    private final long nightEnd;

    public WaypointService(JavaPlugin plugin, DataManager dataManager, KingdomsBridge kingdoms) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.kingdoms = kingdoms;
        this.waypoints = dataManager.loadWaypoints();
        for (WaypointRecord waypoint : waypoints.values()) byBlock.put(waypoint.block(), waypoint.id());
        destinationKey = new NamespacedKey(plugin, "waypoint_destination");
        displayKey = new NamespacedKey(plugin, "waypoint_display");
        displayWaypointKey = new NamespacedKey(plugin, "waypoint_display_id");
        creationCost = plugin.getConfig().getInt("waypoints.creation-cost", 300);
        publicFare = plugin.getConfig().getInt("waypoints.public-fare", 25);
        personalFare = plugin.getConfig().getInt("waypoints.personal-fare", 5);
        accessCost = plugin.getConfig().getInt("waypoints.personal-access-cost", 100);
        warmupSeconds = plugin.getConfig().getInt("waypoints.warmup-seconds", 10);
        safeRadius = plugin.getConfig().getInt("waypoints.safe-radius", 8);
        labelHeight = plugin.getConfig().getDouble("waypoints.label-height", 2.0);
        blockedAtNight = plugin.getConfig().getBoolean("waypoints.blocked-at-night", true);
        nightStart = plugin.getConfig().getLong("waypoints.night-start", 12300);
        nightEnd = plugin.getConfig().getLong("waypoints.night-end", 23850);
    }

    public Collection<WaypointRecord> all() { return Collections.unmodifiableCollection(waypoints.values()); }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            TravelSession session = sessions.remove(playerId);
            if (session == null) continue;
            Bukkit.getScheduler().cancelTask(session.taskId());
            kingdoms.depositPurse(playerId, session.reservedFare());
            if (player != null) Messages.info(player, "Waypoint travel was cancelled because the plugin stopped. Your fare was returned.");
        }
        save();
    }

    public void save() {
        dataManager.saveWaypoints(waypoints.values());
    }

    public WaypointRecord at(Block block) {
        UUID id = byBlock.get(BlockKey.of(block));
        return id == null ? null : waypoints.get(id);
    }

    public boolean isWaypointBlock(Block block) {
        return byBlock.containsKey(BlockKey.of(block));
    }

    public void createPublic(Player player, String name) {
        Block block = player.getTargetBlockExact(6);
        if (block == null || !isCopper(block.getType())) {
            Messages.error(player, "Look directly at a copper block inside your land.");
            return;
        }
        if (isWaypointBlock(block)) {
            Messages.error(player, "That copper block is already a waypoint.");
            return;
        }
        String cleanName = name.trim();
        if (cleanName.isBlank() || cleanName.length() > 40) {
            Messages.error(player, "Waypoint names must be between 1 and 40 characters.");
            return;
        }
        if (waypoints.values().stream().anyMatch(w -> w.type() == WaypointType.PUBLIC && w.name().equalsIgnoreCase(cleanName))) {
            Messages.error(player, "A public waypoint already uses that name.");
            return;
        }
        Object claim = kingdoms.claimAt(block.getLocation());
        if (claim == null || !kingdoms.isPolitical(claim)) {
            Messages.error(player, "Public waypoints must be created inside a political land.");
            return;
        }
        if (!kingdoms.isOwnerOrKnight(claim, player.getUniqueId())) {
            Messages.error(player, "Only the landowner or one of its Knights may create a public waypoint.");
            return;
        }
        UUID claimId = kingdoms.claimId(claim);
        long existing = waypoints.values().stream()
                .filter(w -> w.type() == WaypointType.PUBLIC && claimId.equals(w.claimId())).count();
        int limit = publicLimit(kingdoms.claimRank(claim));
        if (existing >= limit) {
            Messages.error(player, "This land has reached its limit of " + limit + " public waypoints.");
            return;
        }
        if (!kingdoms.withdrawPurse(player.getUniqueId(), creationCost)) {
            Messages.error(player, "You need " + creationCost + "g in your purse.");
            return;
        }
        WaypointRecord record = new WaypointRecord(UUID.randomUUID(), WaypointType.PUBLIC, cleanName,
                BlockKey.of(block), player.getUniqueId(), player.getUniqueId(), claimId, kingdoms.claimName(claim));
        add(record);
        ensureDisplay(record);
        Messages.success(player, "Created public waypoint " + cleanName + " for " + creationCost + "g.");
    }

    public void createPersonal(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null || !isCopper(block.getType())) {
            Messages.error(player, "Look directly at a copper block inside your personal claim.");
            return;
        }
        if (isWaypointBlock(block)) {
            Messages.error(player, "That copper block is already a waypoint.");
            return;
        }
        Object claim = kingdoms.claimAt(block.getLocation());
        if (claim == null || !kingdoms.isPersonal(claim) || !kingdoms.isOwner(claim, player.getUniqueId())) {
            Messages.error(player, "Personal waypoints must be inside your own personal claim.");
            return;
        }
        UUID personalClaimId = kingdoms.claimId(claim);
        boolean already = waypoints.values().stream().anyMatch(w -> w.type() == WaypointType.PERSONAL
                && personalClaimId.equals(w.claimId()));
        if (already) {
            Messages.error(player, "This personal claim already has a waypoint.");
            return;
        }
        if (!kingdoms.withdrawPurse(player.getUniqueId(), creationCost)) {
            Messages.error(player, "You need " + creationCost + "g in your purse.");
            return;
        }
        WaypointRecord record = new WaypointRecord(UUID.randomUUID(), WaypointType.PERSONAL,
                player.getName() + "'s Personal Waypoint", BlockKey.of(block), player.getUniqueId(),
                player.getUniqueId(), kingdoms.claimId(claim), kingdoms.claimName(claim));
        add(record);
        ensureDisplay(record);
        Messages.success(player, "Created your personal waypoint for " + creationCost + "g.");
    }

    public void access(Player owner, boolean add, OfflinePlayer target) {
        WaypointRecord record = lookingAt(owner);
        if (record == null || record.type() != WaypointType.PERSONAL) {
            Messages.error(owner, "Look at your personal waypoint.");
            return;
        }
        if (!record.ownerId().equals(owner.getUniqueId())) {
            Messages.error(owner, "Only the personal waypoint's owner can manage access.");
            return;
        }
        UUID targetId = target.getUniqueId();
        if (add) {
            if (targetId.equals(owner.getUniqueId()) || record.authorized().contains(targetId)) {
                Messages.error(owner, "That player already has access.");
                return;
            }
            if (!kingdoms.withdrawPurse(owner.getUniqueId(), accessCost)) {
                Messages.error(owner, "You need " + accessCost + "g in your purse.");
                return;
            }
            record.authorized().add(targetId);
            record.storedGold(record.storedGold() + accessCost);
            save();
            Messages.success(owner, "Added " + displayName(target) + " for " + accessCost + "g. The fee is stored in the waypoint.");
        } else {
            if (!record.authorized().remove(targetId)) {
                Messages.error(owner, "That player does not have access.");
                return;
            }
            save();
            Messages.success(owner, "Removed " + displayName(target) + " from the waypoint access list.");
        }
    }

    public void removeLooking(Player player) {
        WaypointRecord record = lookingAt(player);
        if (record == null) {
            Messages.error(player, "Look directly at the waypoint you want to remove.");
            return;
        }
        boolean allowed;
        if (record.type() == WaypointType.PERSONAL) {
            allowed = record.ownerId().equals(player.getUniqueId());
        } else {
            Object linked = kingdoms.claim(record.claimId());
            Object current = record.block().location() == null ? null : kingdoms.claimAt(record.block().location());
            allowed = (linked != null && kingdoms.isOwnerOrKnight(linked, player.getUniqueId()))
                    || (linked == null && current == null && record.creatorId().equals(player.getUniqueId()))
                    || (linked == null && current != null && kingdoms.isOwnerOrKnight(current, player.getUniqueId()));
        }
        if (!allowed && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "You are not allowed to remove this waypoint.");
            return;
        }
        long refund = creationCost + record.storedGold();
        kingdoms.depositPurse(player.getUniqueId(), refund);
        remove(record);
        Messages.success(player, "Removed " + record.name() + " and returned " + refund + "g to your purse.");
    }

    public void adoptLooking(Player player) {
        WaypointRecord record = lookingAt(player);
        if (record == null || record.type() != WaypointType.PUBLIC) {
            Messages.error(player, "Look directly at an orphaned public waypoint.");
            return;
        }
        if (kingdoms.claim(record.claimId()) != null) {
            Messages.error(player, "That waypoint still belongs to an existing land.");
            return;
        }
        Location location = record.block().location();
        Object claim = location == null ? null : kingdoms.claimAt(location);
        if (claim == null || !kingdoms.isPolitical(claim) || !kingdoms.isOwnerOrKnight(claim, player.getUniqueId())) {
            Messages.error(player, "Your political land must control this chunk, and you must be its owner or a Knight.");
            return;
        }
        UUID newClaimId = kingdoms.claimId(claim);
        long existing = waypoints.values().stream()
                .filter(w -> w.type() == WaypointType.PUBLIC && newClaimId.equals(w.claimId())).count();
        int limit = publicLimit(kingdoms.claimRank(claim));
        if (existing >= limit) {
            Messages.error(player, "This land has reached its limit of " + limit + " public waypoints.");
            return;
        }
        record.claimId(newClaimId);
        record.claimName(kingdoms.claimName(claim));
        if (record.storedGold() > 0) {
            kingdoms.depositTreasury(record.claimId(), record.storedGold());
            record.storedGold(0);
        }
        save();
        ensureDisplay(record);
        Messages.success(player, "Adopted " + record.name() + " for " + record.claimName() + ".");
    }

    public void infoLooking(Player player) {
        WaypointRecord record = lookingAt(player);
        if (record == null) {
            Messages.error(player, "Look directly at a waypoint.");
            return;
        }
        Messages.info(player, record.name() + " — " + record.type() + ", stored balance " + record.storedGold() + "g"
                + (record.claimName() == null ? "" : ", claim " + record.claimName()) + ".");
    }

    public boolean interact(Player player, Block block) {
        WaypointRecord source = at(block);
        if (source == null) return false;
        if (!source.canUse(player.getUniqueId())) {
            Messages.error(player, "You do not have access to this personal waypoint.");
            return true;
        }
        if (isClosed(block.getWorld())) {
            Messages.error(player, "Waypoints are closed until dawn.");
            return true;
        }
        openMenu(player, source, 0);
        return true;
    }

    public void clickMenu(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WaypointMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String value = meta.getPersistentDataContainer().get(destinationKey, PersistentDataType.STRING);
        if (value == null) return;
        if (value.equals("previous")) {
            WaypointRecord source = waypoints.get(holder.sourceId());
            if (source != null) openMenu(player, source, holder.page() - 1);
            return;
        }
        if (value.equals("next")) {
            WaypointRecord source = waypoints.get(holder.sourceId());
            if (source != null) openMenu(player, source, holder.page() + 1);
            return;
        }
        try {
            WaypointRecord source = waypoints.get(holder.sourceId());
            WaypointRecord destination = waypoints.get(UUID.fromString(value));
            if (source == null || destination == null) {
                Messages.error(player, "That waypoint is no longer available.");
                return;
            }
            player.closeInventory();
            startTravel(player, source, destination);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void cancel(Player player, String reason) {
        TravelSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId());
        kingdoms.depositPurse(player.getUniqueId(), session.reservedFare());
        Messages.error(player, reason + " Your fare was returned.");
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public Location sessionOrigin(UUID playerId) {
        TravelSession session = sessions.get(playerId);
        return session == null ? null : session.origin();
    }

    public void tickDisplays() {
        for (WaypointRecord waypoint : waypoints.values()) ensureDisplay(waypoint);
    }

    public void ensureAllDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) display.remove();
            }
        }
        displayByWaypoint.clear();
        for (WaypointRecord waypoint : waypoints.values()) ensureDisplay(waypoint);
    }

    private void openMenu(Player player, WaypointRecord source, int requestedPage) {
        List<WaypointRecord> destinations = waypoints.values().stream()
                .filter(w -> !w.id().equals(source.id()))
                .filter(w -> w.canUse(player.getUniqueId()))
                .filter(this::blockStillExists)
                .sorted(Comparator.comparing(WaypointRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pageCount = Math.max(1, (int) Math.ceil(destinations.size() / 45.0));
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        WaypointMenuHolder holder = new WaypointMenuHolder(source.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Waypoint Travel", NamedTextColor.GOLD));
        holder.inventory(inventory);
        int start = page * 45;
        for (int i = 0; i < 45 && start + i < destinations.size(); i++) {
            WaypointRecord destination = destinations.get(start + i);
            int fare = destination.type() == WaypointType.PUBLIC ? publicFare : personalFare;
            ItemStack icon = new ItemStack(Material.COPPER_BLOCK);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(destination.name(), NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(destination.type() == WaypointType.PUBLIC ? "Public waypoint" : "Personal waypoint", NamedTextColor.GRAY));
            if (destination.claimName() != null) lore.add(Component.text("Claim: " + destination.claimName(), NamedTextColor.GRAY));
            lore.add(Component.text("Fare: " + fare + "g", NamedTextColor.GOLD));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(destinationKey, PersistentDataType.STRING, destination.id().toString());
            icon.setItemMeta(meta);
            inventory.setItem(i, icon);
        }
        if (page > 0) inventory.setItem(45, navItem("Previous Page", "previous"));
        if (page + 1 < pageCount) inventory.setItem(53, navItem("Next Page", "next"));
        player.openInventory(inventory);
    }

    private ItemStack navItem(String name, String value) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(destinationKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private void startTravel(Player player, WaypointRecord source, WaypointRecord destination) {
        if (hasSession(player.getUniqueId())) {
            Messages.error(player, "You are already preparing to travel.");
            return;
        }
        if (!source.canUse(player.getUniqueId()) || !destination.canUse(player.getUniqueId())) {
            Messages.error(player, "You no longer have access to one of those waypoints.");
            return;
        }
        Location sourceLocation = source.block().location();
        Location destinationLocation = destination.block().location();
        if (sourceLocation == null || destinationLocation == null || !blockStillExists(source) || !blockStillExists(destination)) {
            Messages.error(player, "One of those waypoints is unavailable.");
            return;
        }
        if (isClosed(sourceLocation.getWorld()) || isClosed(destinationLocation.getWorld())) {
            Messages.error(player, "Waypoints are closed until dawn.");
            return;
        }
        long fare = destination.type() == WaypointType.PUBLIC ? publicFare : personalFare;
        if (!kingdoms.withdrawPurse(player.getUniqueId(), fare)) {
            Messages.error(player, "You need " + fare + "g in your purse.");
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
                kingdoms.depositPurse(player.getUniqueId(), fare);
                return;
            }
            if (isClosed(sourceLocation.getWorld()) || isClosed(destinationLocation.getWorld())) {
                cancel(player, "Night fell before the waypoint could activate.");
                return;
            }
            if (remaining[0] <= 0) {
                completeTravel(player, destination, fare);
                return;
            }
            player.sendActionBar(Component.text("Travelling to " + destination.name() + " in " + remaining[0]
                    + " seconds — Move or sneak to cancel. Teleport will be canceled if damage is taken.", NamedTextColor.AQUA));
            remaining[0]--;
        }, 0L, 20L);
        sessions.put(player.getUniqueId(), new TravelSession(player.getUniqueId(), destination.id(), origin, fare,
                System.currentTimeMillis(), taskId, "waypoint"));
    }

    private void completeTravel(Player player, WaypointRecord destination, long fare) {
        TravelSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.taskId());
        Location center = destination.block().location();
        if (center == null) {
            kingdoms.depositPurse(player.getUniqueId(), fare);
            Messages.error(player, "That waypoint's world is unavailable. Your fare was returned.");
            return;
        }
        center.getChunk().load();
        if (!isCopper(center.getBlock().getType())) {
            kingdoms.depositPurse(player.getUniqueId(), fare);
            Messages.error(player, "That waypoint no longer exists. Your fare was returned.");
            return;
        }
        Location safe = WorldUtil.findSafePlayerArrival(center, safeRadius);
        if (safe == null) {
            kingdoms.depositPurse(player.getUniqueId(), fare);
            Messages.error(player, "No safe arrival space could be found. Your fare was returned.");
            return;
        }
        if (!player.teleport(safe, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            kingdoms.depositPurse(player.getUniqueId(), fare);
            Messages.error(player, "The teleport failed. Your fare was returned.");
            return;
        }
        if (destination.type() == WaypointType.PUBLIC && kingdoms.claim(destination.claimId()) != null) {
            kingdoms.depositTreasury(destination.claimId(), fare);
        } else {
            destination.storedGold(destination.storedGold() + fare);
            save();
        }
        Messages.success(player, "Arrived at " + destination.name() + ".");
    }

    private int publicLimit(String rank) {
        if (rank == null) return plugin.getConfig().getInt("waypoints.public-limit-default", 4);
        return plugin.getConfig().getInt("waypoints.public-limits." + rank,
                plugin.getConfig().getInt("waypoints.public-limit-default", 4));
    }

    private WaypointRecord lookingAt(Player player) {
        Block block = player.getTargetBlockExact(6);
        return block == null ? null : at(block);
    }

    private void add(WaypointRecord record) {
        waypoints.put(record.id(), record);
        byBlock.put(record.block(), record.id());
        save();
    }

    private void remove(WaypointRecord record) {
        waypoints.remove(record.id());
        byBlock.remove(record.block());
        UUID displayId = displayByWaypoint.remove(record.id());
        if (displayId != null) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) entity.remove();
        }
        save();
    }

    private boolean blockStillExists(WaypointRecord waypoint) {
        Location location = waypoint.block().location();
        if (location == null) return false;
        World world = location.getWorld();
        int chunkX = waypoint.block().x() >> 4;
        int chunkZ = waypoint.block().z() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return true;
        return isCopper(world.getBlockAt(waypoint.block().x(), waypoint.block().y(), waypoint.block().z()).getType());
    }

    private boolean isClosed(World world) {
        return blockedAtNight && WorldUtil.isNight(world, nightStart, nightEnd);
    }

    private void ensureDisplay(WaypointRecord waypoint) {
        Location location = waypoint.block().location();
        if (location == null) return;
        int chunkX = waypoint.block().x() >> 4;
        int chunkZ = waypoint.block().z() >> 4;
        if (!location.getWorld().isChunkLoaded(chunkX, chunkZ) || !blockStillExists(waypoint)) return;
        TextDisplay display = null;
        UUID existingId = displayByWaypoint.get(waypoint.id());
        if (existingId != null && Bukkit.getEntity(existingId) instanceof TextDisplay text && text.isValid()) display = text;
        if (display == null) {
            Location displayLocation = location.clone().add(0.5, labelHeight, 0.5);
            display = location.getWorld().spawn(displayLocation, TextDisplay.class, text -> {
                text.setBillboard(Display.Billboard.CENTER);
                text.setSeeThrough(true);
                text.setShadowed(true);
                text.setPersistent(false);
                text.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
                text.getPersistentDataContainer().set(displayWaypointKey, PersistentDataType.STRING, waypoint.id().toString());
            });
            displayByWaypoint.put(waypoint.id(), display.getUniqueId());
        }
        if (isClosed(location.getWorld())) {
            display.text(Component.text("[Closed Until Dawn]", NamedTextColor.RED));
        } else if (waypoint.type() == WaypointType.PUBLIC) {
            String creator = displayName(Bukkit.getOfflinePlayer(waypoint.creatorId()));
            String claim = waypoint.claimName() == null ? "No Claim" : waypoint.claimName();
            display.text(Component.text("[Public Waypoint] owned by " + claim + ", created by " + creator,
                    NamedTextColor.GOLD));
        } else {
            display.text(Component.text("[Personal Waypoint] owned by "
                    + displayName(Bukkit.getOfflinePlayer(waypoint.ownerId())), NamedTextColor.AQUA));
        }
    }

    private boolean isCopper(Material material) {
        return material == Material.COPPER_BLOCK || material == Material.EXPOSED_COPPER
                || material == Material.WEATHERED_COPPER || material == Material.OXIDIZED_COPPER
                || material == Material.WAXED_COPPER_BLOCK || material == Material.WAXED_EXPOSED_COPPER
                || material == Material.WAXED_WEATHERED_COPPER || material == Material.WAXED_OXIDIZED_COPPER;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }
}
