package com.tiyler.roadstransport.service;

import com.tiyler.roadstransport.data.DataManager;
import com.tiyler.roadstransport.gui.HorseCargoHolder;
import com.tiyler.roadstransport.model.ChunkKey;
import com.tiyler.roadstransport.model.HorseRecord;
import com.tiyler.roadstransport.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class HorseService {
    private static final int CARAVAN_PAGE_SLOTS = 27;
    private static final int CARAVAN_MENU_SIZE = 36;
    private static final int PREVIOUS_PAGE_SLOT = 27;
    private static final int PAGE_INFO_SLOT = 31;
    private static final int NEXT_PAGE_SLOT = 35;

    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, HorseRecord> horses;
    private final Map<UUID, ChunkKey> lastChunks = new HashMap<>();
    private final Map<UUID, CargoSession> cargoSessions = new HashMap<>();
    private final Map<UUID, UUID> cargoLocks = new HashMap<>();
    private final int cargoSlots;
    private final int maximumTier;
    private final double multiplierPerTier;
    private final double inheritanceChance;
    private final double enchantedAppleSuccessChance;
    private final long minimumCaravanGold;
    private final int rewardEveryChunks;
    private final int maximumChunkStep;
    private final double hostileRadius;
    private final boolean excludeCreepers;
    private final long nightStart;
    private final long nightEnd;
    private final int maximumCaravanHorses;
    private final double speedPenaltyPerAdditionalHorse;
    private final double minimumCaravanSpeedMultiplier;
    private boolean shuttingDown;

    public HorseService(JavaPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        horses = dataManager.loadHorses();
        int configuredCargoSlots = plugin.getConfig().getInt("horses.cargo-slots", 54);
        cargoSlots = Math.max(9, Math.min(54, (configuredCargoSlots / 9) * 9));
        maximumTier = plugin.getConfig().getInt("horses.maximum-speed-tier", 3);
        multiplierPerTier = plugin.getConfig().getDouble("horses.speed-multiplier-per-tier", 0.15);
        inheritanceChance = plugin.getConfig().getDouble("horses.inheritance-chance-per-parent", 0.50);
        enchantedAppleSuccessChance = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("horses.enchanted-apple-success-chance", 0.25)));
        minimumCaravanGold = plugin.getConfig().getLong("horses.minimum-caravan-gold", 9);
        rewardEveryChunks = Math.max(1, plugin.getConfig().getInt("horses.reward-every-unique-chunks", 4));
        maximumChunkStep = Math.max(1, plugin.getConfig().getInt("horses.maximum-legitimate-chunk-step", 1));
        hostileRadius = plugin.getConfig().getDouble("horses.hostile-target-radius", 24.0);
        excludeCreepers = plugin.getConfig().getBoolean("horses.exclude-creepers-from-cargo-targeting", false);
        nightStart = plugin.getConfig().getLong("waypoints.night-start", 12300);
        nightEnd = plugin.getConfig().getLong("waypoints.night-end", 23850);
        maximumCaravanHorses = Math.max(1, Math.min(4,
                plugin.getConfig().getInt("horses.maximum-linked-horses", 4)));
        speedPenaltyPerAdditionalHorse = Math.max(0.0, Math.min(0.75,
                plugin.getConfig().getDouble("horses.speed-penalty-per-additional-horse", 0.10)));
        minimumCaravanSpeedMultiplier = Math.max(0.10, Math.min(1.0,
                plugin.getConfig().getDouble("horses.minimum-caravan-speed-multiplier", 0.55)));
    }

    public void save() {
        dataManager.saveHorses(horses.values());
    }

    public void shutdown() {
        shuttingDown = true;
        for (CargoSession session : new ArrayList<>(cargoSessions.values())) {
            saveOpenPage(session);
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.getOpenInventory().getTopInventory().equals(session.inventory)) {
                player.closeInventory();
            }
            releaseSession(session.playerId);
        }
        cargoSessions.clear();
        cargoLocks.clear();
        save();
    }

    public void handleInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Horse horse)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        HorseRecord record = record(horse);

        if (held.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            event.setCancelled(true);
            upgradeHorse(player, horse, record, held);
            return;
        }

        if (!record.cargoAttached() && held.getType() == Material.CHEST) {
            event.setCancelled(true);
            attachCargo(player, horse, record, held);
            return;
        }

        if (record.cargoAttached() && player.isSneaking() && held.getType().isAir()) {
            event.setCancelled(true);
            openCargo(player, horse, record);
        }
    }

    public void handleLeash(PlayerLeashEntityEvent event) {
        if (!(event.getEntity() instanceof Horse target)
                || !(event.getLeashHolder() instanceof Player player)) return;

        Horse root = heldCaravanRoot(player, target);
        if (root == null) return; // First horse: let vanilla leash it to the player normally.

        List<Horse> chain = caravanChain(root);
        if (chain.contains(target)) return;
        if (chain.size() >= maximumCaravanHorses) {
            event.setCancelled(true);
            Messages.error(player, "The caravan cannot lead any more horses");
            return;
        }

        HorseRecord rootRecord = record(root);
        HorseRecord targetRecord = record(target);
        if (!rootRecord.cargoAttached() || !targetRecord.cargoAttached()) {
            event.setCancelled(true);
            Messages.error(player, "Both horses must have cargo crates attached before they can join a caravan.");
            return;
        }

        boolean admin = player.hasPermission("roadsandtransport.admin");
        if ((!isOwner(player, root, rootRecord) || !isOwner(player, target, targetRecord)) && !admin) {
            event.setCancelled(true);
            Messages.error(player, "Only your own cargo horses can be linked into a caravan.");
            return;
        }
        if (!admin && !Objects.equals(rootRecord.ownerId(), targetRecord.ownerId())) {
            event.setCancelled(true);
            Messages.error(player, "All horses in a caravan must have the same owner.");
            return;
        }

        Horse tail = chain.get(chain.size() - 1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || !tail.isValid()) return;
            Entity currentHolder = leashHolder(target);
            if (currentHolder != player) return;
            if (!target.setLeashHolder(tail)) {
                Messages.error(player, "That horse could not be linked to the caravan.");
                return;
            }
            targetRecord.clearJourney();
            lastChunks.put(target.getUniqueId(), ChunkKey.of(target.getLocation()));
            applyCaravanSpeed(caravanChain(root));
            save();
            Messages.success(player, "Horse added to caravan (" + (chain.size() + 1) + "/"
                    + maximumCaravanHorses + ").");
        });
    }

    public void handleBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Horse child)
                || !(event.getMother() instanceof Horse mother)
                || !(event.getFather() instanceof Horse father)) return;
        HorseRecord childRecord = record(child);
        int inherited = 0;
        HorseRecord motherRecord = horses.get(mother.getUniqueId());
        HorseRecord fatherRecord = horses.get(father.getUniqueId());
        if (motherRecord != null && motherRecord.speedTier() > 0
                && ThreadLocalRandom.current().nextDouble() < inheritanceChance) {
            inherited = Math.max(inherited, motherRecord.speedTier());
        }
        if (fatherRecord != null && fatherRecord.speedTier() > 0
                && ThreadLocalRandom.current().nextDouble() < inheritanceChance) {
            inherited = Math.max(inherited, fatherRecord.speedTier());
        }
        childRecord.speedTier(Math.min(maximumTier, inherited));
        captureBaseSpeed(child, childRecord);
        applySpeed(child, childRecord, 1, cargoGold(childRecord));
        save();
    }

    public void handleTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        Location destination = event.getTo();
        if (destination == null) {
            lastChunks.remove(horse.getUniqueId());
            return;
        }
        lastChunks.put(horse.getUniqueId(), ChunkKey.of(destination));
        // Teleports and portals never count toward caravan progress. Updating only the
        // last-known chunk prevents the next normal movement scan from treating the jump as travel.
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HorseCargoHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CargoSession session = cargoSessions.get(player.getUniqueId());
        if (session == null || !session.rootHorseId.equals(holder.rootHorseId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= holder.storageSlots() && rawSlot < CARAVAN_PAGE_SLOTS) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot < CARAVAN_PAGE_SLOTS || rawSlot >= CARAVAN_MENU_SIZE) return;
        event.setCancelled(true);

        if (rawSlot == PREVIOUS_PAGE_SLOT && holder.pageIndex() > 0) {
            switchCargoPage(player, session, holder.pageIndex() - 1);
        } else if (rawSlot == NEXT_PAGE_SLOT && holder.pageIndex() + 1 < holder.totalPages()) {
            switchCargoPage(player, session, holder.pageIndex() + 1);
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HorseCargoHolder)) return;
        HorseCargoHolder holder = (HorseCargoHolder) event.getView().getTopInventory().getHolder();
        for (int rawSlot : event.getRawSlots()) {
            if ((rawSlot >= holder.storageSlots() && rawSlot < CARAVAN_PAGE_SLOTS)
                    || (rawSlot >= CARAVAN_PAGE_SLOTS && rawSlot < CARAVAN_MENU_SIZE)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        if (shuttingDown || !(event.getInventory().getHolder() instanceof HorseCargoHolder holder)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        CargoSession session = cargoSessions.get(playerId);
        if (session == null || !session.rootHorseId.equals(holder.rootHorseId())) return;

        copyPageToRecord(event.getInventory(), holder);
        if (!session.switchingPages) {
            releaseSession(playerId);
            save();
        }
    }

    public void handleQuit(PlayerQuitEvent event) {
        CargoSession session = cargoSessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        saveOpenPage(session);
        releaseSession(event.getPlayer().getUniqueId());
        save();
    }

    public void handleDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        closeSessionsContaining(horse.getUniqueId());
        HorseRecord record = horses.remove(horse.getUniqueId());
        if (record == null) return;
        if (record.cargoAttached()) {
            event.getDrops().add(new ItemStack(Material.CHEST));
            for (ItemStack item : record.cargo()) {
                if (item != null && !item.getType().isAir()) event.getDrops().add(item.clone());
            }
        }
        lastChunks.remove(horse.getUniqueId());
        save();
    }

    public void inspect(Player player) {
        Horse horse = targetHorse(player);
        if (horse == null) {
            Messages.error(player, "Look directly at a horse within six blocks.");
            return;
        }
        HorseRecord record = record(horse);
        Horse root = chainRoot(horse);
        List<Horse> chain = root.getUniqueId().equals(horse.getUniqueId())
                ? caravanChain(root) : List.of(horse);
        long gold = caravanGold(chain);
        int capacity = cargoSlots + Math.max(0, chain.size() - 1) * CARAVAN_PAGE_SLOTS;
        int occupied = sharedCargoOccupied(chain);
        int penaltyPercent = (int) Math.round((1.0 - caravanSpeedMultiplier(chain.size())) * 100.0);

        Messages.info(player, "Horse speed tier: " + record.speedTier() + "/" + maximumTier + ".");
        Messages.info(player, "Caravan horses: " + chain.size() + "/" + maximumCaravanHorses + ".");
        Messages.info(player, "Accessible caravan cargo: " + occupied + "/" + capacity + " slots.");
        Messages.info(player, "Caravan speed penalty: " + penaltyPercent + "%.");
        Messages.info(player, "Caravan gold: " + gold + "g.");
        HorseRecord journeyRecord = record(chain.get(0));
        int remaining = rewardEveryChunks - journeyRecord.partialChunkProgress();
        Messages.info(player, "Caravan progress: " + journeyRecord.partialChunkProgress() + "/" + rewardEveryChunks
                + " unique chunks; " + remaining + " until the next 1g reward.");
    }

    public void trust(Player player, OfflinePlayer target, boolean add) {
        Horse horse = targetHorse(player);
        if (horse == null) {
            Messages.error(player, "Look directly at a horse within six blocks.");
            return;
        }
        HorseRecord record = record(horse);
        if (!isOwner(player, horse, record) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "Only the horse's owner can change cargo access.");
            return;
        }
        if (add) {
            if (!record.trusted().add(target.getUniqueId())) {
                Messages.error(player, "That player is already trusted with this horse.");
                return;
            }
            Messages.success(player, "Trusted " + nameOf(target) + " with this horse's cargo.");
        } else {
            if (!record.trusted().remove(target.getUniqueId())) {
                Messages.error(player, "That player was not trusted with this horse.");
                return;
            }
            Messages.success(player, "Removed " + nameOf(target) + " from this horse's cargo access.");
        }
        save();
    }

    public void scanMovement() {
        boolean changed = false;
        Set<UUID> processed = new HashSet<>();
        for (HorseRecord initialRecord : new ArrayList<>(horses.values())) {
            Entity entity = Bukkit.getEntity(initialRecord.horseId());
            if (!(entity instanceof Horse horse) || !horse.isValid() || processed.contains(horse.getUniqueId())) continue;

            Horse root = chainRoot(horse);
            List<Horse> chain = caravanChain(root);
            for (Horse member : chain) processed.add(member.getUniqueId());

            long gold = caravanGold(chain);
            for (int i = 0; i < chain.size(); i++) {
                Horse member = chain.get(i);
                HorseRecord memberRecord = record(member);
                captureBaseSpeed(member, memberRecord);
                applySpeed(member, memberRecord, chain.size(), gold);
                if (i > 0) {
                    if (!memberRecord.visitedChunks().isEmpty() || memberRecord.partialChunkProgress() != 0) {
                        memberRecord.clearJourney();
                        changed = true;
                    }
                    lastChunks.put(member.getUniqueId(), ChunkKey.of(member.getLocation()));
                }
            }

            HorseRecord rootRecord = record(root);
            if (gold == 0) {
                if (!rootRecord.visitedChunks().isEmpty() || rootRecord.partialChunkProgress() != 0) {
                    rootRecord.clearJourney();
                    changed = true;
                }
                lastChunks.put(root.getUniqueId(), ChunkKey.of(root.getLocation()));
                continue;
            }
            if (gold < minimumCaravanGold || !legitimateHandler(root)) {
                lastChunks.put(root.getUniqueId(), ChunkKey.of(root.getLocation()));
                continue;
            }

            ChunkKey current = ChunkKey.of(root.getLocation());
            ChunkKey previous = lastChunks.put(root.getUniqueId(), current);
            if (previous == null) {
                rootRecord.visitedChunks().add(current);
                changed = true;
                continue;
            }
            if (current.equals(previous)) continue;
            if (!current.adjacentOrSame(previous, maximumChunkStep)) continue;
            if (!rootRecord.visitedChunks().add(current)) continue;
            rootRecord.partialChunkProgress(rootRecord.partialChunkProgress() + 1);
            changed = true;
            while (rootRecord.partialChunkProgress() >= rewardEveryChunks) {
                rootRecord.partialChunkProgress(rootRecord.partialChunkProgress() - rewardEveryChunks);
                rootRecord.pendingRewardGold(rootRecord.pendingRewardGold() + 1);
            }
            if (flushPendingRewards(rootRecord)) changed = true;
        }
        if (changed) save();
    }

    public void targetCargoHorsesAtNight() {
        for (HorseRecord record : horses.values()) {
            if (!record.cargoAttached()) continue;
            Entity entity = Bukkit.getEntity(record.horseId());
            if (!(entity instanceof Horse horse) || !horse.isValid()) continue;
            long time = horse.getWorld().getTime();
            if (!(time >= nightStart && time <= nightEnd)) continue;
            for (Entity nearby : horse.getNearbyEntities(hostileRadius, hostileRadius, hostileRadius)) {
                if (!(nearby instanceof Monster monster)) continue;
                if (excludeCreepers && monster instanceof Creeper) continue;
                monster.setTarget(horse);
            }
        }
    }

    public HorseRecord record(Horse horse) {
        HorseRecord record = horses.computeIfAbsent(horse.getUniqueId(), HorseRecord::new);
        if (record.ownerId() == null) record.ownerId(horse.getOwnerUniqueId());
        captureBaseSpeed(horse, record);
        ensureCargoSize(record);
        return record;
    }

    private void upgradeHorse(Player player, Horse horse, HorseRecord record, ItemStack held) {
        if (!isOwner(player, horse, record) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "Only the horse's owner may permanently improve it.");
            return;
        }
        if (record.enchantedAppleUpgradeApplied()) {
            Messages.error(player, "Your horse doesn't seem interested in having another apple");
            return;
        }
        if (record.speedTier() >= maximumTier) {
            Messages.error(player, "Your horse seems as fast as it could possibly be");
            return;
        }
        held.setAmount(held.getAmount() - 1);
        if (ThreadLocalRandom.current().nextDouble() >= enchantedAppleSuccessChance) {
            Messages.info(player, "Your horse doesn't seem any different");
            return;
        }
        record.speedTier(record.speedTier() + 1);
        record.enchantedAppleUpgradeApplied(true);
        Horse root = chainRoot(horse);
        applyCaravanSpeed(caravanChain(root));
        save();
        Messages.success(player, "Your horse's eyes seem to be invigorated");
    }

    private void attachCargo(Player player, Horse horse, HorseRecord record, ItemStack chest) {
        if (!isOwner(player, horse, record) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "Only the horse's owner may attach cargo storage.");
            return;
        }
        ItemStack armor = horse.getInventory().getArmor();
        if (armor == null || armor.getType().isAir()) {
            Messages.error(player, "The horse must be wearing horse armor before a cargo chest can be attached.");
            return;
        }
        chest.setAmount(chest.getAmount() - 1);
        record.cargoAttached(true);
        record.ownerId(player.getUniqueId());
        save();
        Messages.success(player, "Cargo crate attached- Sneak right-click your horse with an empty hand to access your cargo");
    }

    private void openCargo(Player player, Horse horse, HorseRecord record) {
        Horse root = chainRoot(horse);
        boolean openingRoot = root.getUniqueId().equals(horse.getUniqueId());
        List<Horse> chain = openingRoot ? caravanChain(root) : List.of(horse);
        HorseRecord accessRecord = openingRoot ? record(root) : record;
        if (!accessRecord.mayAccess(player.getUniqueId()) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "You do not have permission to open this horse's cargo.");
            return;
        }

        List<UUID> chainIds = chain.stream().map(Entity::getUniqueId).toList();
        for (UUID horseId : chainIds) {
            UUID lockOwner = cargoLocks.get(horseId);
            if (lockOwner != null && !lockOwner.equals(player.getUniqueId())) {
                Messages.error(player, "Someone else is already using this caravan's cargo.");
                return;
            }
        }

        releaseSession(player.getUniqueId());
        CargoSession session = new CargoSession(player.getUniqueId(), chain.get(0).getUniqueId(), chainIds);
        cargoSessions.put(player.getUniqueId(), session);
        for (UUID horseId : chainIds) cargoLocks.put(horseId, player.getUniqueId());
        openCargoPage(player, session, 0);
    }

    private void openCargoPage(Player player, CargoSession session, int requestedPage) {
        int basePages = Math.max(1, (cargoSlots + CARAVAN_PAGE_SLOTS - 1) / CARAVAN_PAGE_SLOTS);
        int totalPages = basePages + Math.max(0, session.chainHorseIds.size() - 1);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        UUID sourceHorseId;
        int sourceOffset;
        int storageSlots;
        if (page < basePages) {
            sourceHorseId = session.chainHorseIds.get(0);
            sourceOffset = page * CARAVAN_PAGE_SLOTS;
            storageSlots = Math.min(CARAVAN_PAGE_SLOTS, cargoSlots - sourceOffset);
        } else {
            int followerIndex = page - basePages + 1;
            sourceHorseId = session.chainHorseIds.get(followerIndex);
            sourceOffset = 0;
            storageSlots = CARAVAN_PAGE_SLOTS;
        }

        HorseRecord sourceRecord = horses.get(sourceHorseId);
        if (sourceRecord == null) {
            Messages.error(player, "That caravan horse is no longer available.");
            releaseSession(player.getUniqueId());
            return;
        }
        ensureCargoSize(sourceRecord);

        HorseCargoHolder holder = new HorseCargoHolder(session.rootHorseId, sourceHorseId, page,
                totalPages, sourceOffset, storageSlots);
        Inventory inventory = Bukkit.createInventory(holder, CARAVAN_MENU_SIZE,
                Component.text("Caravan Cargo — Page " + (page + 1) + "/" + totalPages, NamedTextColor.GOLD));
        holder.inventory(inventory);

        for (int i = 0; i < storageSlots; i++) {
            ItemStack item = sourceRecord.cargo().get(sourceOffset + i);
            inventory.setItem(i, item == null ? null : item.clone());
        }
        for (int i = storageSlots; i < CARAVAN_PAGE_SLOTS; i++) {
            inventory.setItem(i, menuItem(Material.BLACK_STAINED_GLASS_PANE, "Unavailable slot"));
        }
        for (int i = CARAVAN_PAGE_SLOTS; i < CARAVAN_MENU_SIZE; i++) {
            inventory.setItem(i, menuItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        if (page > 0) inventory.setItem(PREVIOUS_PAGE_SLOT, menuItem(Material.ARROW, "Previous cargo page"));
        if (page + 1 < totalPages) inventory.setItem(NEXT_PAGE_SLOT, menuItem(Material.ARROW, "Next cargo page"));
        inventory.setItem(PAGE_INFO_SLOT, menuItem(Material.CHEST,
                "Cargo page " + (page + 1) + " of " + totalPages));

        session.inventory = inventory;
        session.pageIndex = page;
        player.openInventory(inventory);
    }

    private void switchCargoPage(Player player, CargoSession session, int page) {
        if (session.switchingPages) return;
        saveOpenPage(session);
        save();
        session.switchingPages = true;
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            CargoSession current = cargoSessions.get(player.getUniqueId());
            if (current != session || !player.isOnline()) {
                releaseSession(player.getUniqueId());
                return;
            }
            current.switchingPages = false;
            openCargoPage(player, current, page);
        });
    }

    private void saveOpenPage(CargoSession session) {
        if (session.inventory == null || !(session.inventory.getHolder() instanceof HorseCargoHolder holder)) return;
        copyPageToRecord(session.inventory, holder);
    }

    private void copyPageToRecord(Inventory inventory, HorseCargoHolder holder) {
        HorseRecord record = horses.get(holder.sourceHorseId());
        if (record == null) return;
        ensureCargoSize(record);
        for (int i = 0; i < holder.storageSlots(); i++) {
            ItemStack item = inventory.getItem(i);
            record.cargo().set(holder.sourceOffset() + i, item == null ? null : item.clone());
        }
    }

    private void releaseSession(UUID playerId) {
        CargoSession session = cargoSessions.remove(playerId);
        if (session == null) return;
        for (UUID horseId : session.chainHorseIds) {
            cargoLocks.remove(horseId, playerId);
        }
    }

    private void closeSessionsContaining(UUID horseId) {
        for (CargoSession session : new ArrayList<>(cargoSessions.values())) {
            if (!session.chainHorseIds.contains(horseId)) continue;
            saveOpenPage(session);
            releaseSession(session.playerId);
            Player viewer = Bukkit.getPlayer(session.playerId);
            if (viewer != null) viewer.closeInventory();
        }
    }

    private void ensureCargoSize(HorseRecord record) {
        while (record.cargo().size() < cargoSlots) record.cargo().add(null);
        while (record.cargo().size() > cargoSlots) record.cargo().remove(record.cargo().size() - 1);
    }

    private long cargoGold(HorseRecord record) {
        long total = 0;
        for (ItemStack item : record.cargo()) {
            if (item == null) continue;
            total += switch (item.getType()) {
                case GOLD_NUGGET -> item.getAmount();
                case GOLD_INGOT -> (long) item.getAmount() * 9L;
                case GOLD_BLOCK -> (long) item.getAmount() * 81L;
                default -> 0L;
            };
        }
        return total;
    }

    private long caravanGold(List<Horse> chain) {
        long total = 0;
        for (Horse horse : chain) total += cargoGold(record(horse));
        return total;
    }

    private int sharedCargoOccupied(List<Horse> chain) {
        if (chain.isEmpty()) return 0;
        int occupied = countOccupied(record(chain.get(0)), 0, cargoSlots);
        for (int i = 1; i < chain.size(); i++) {
            occupied += countOccupied(record(chain.get(i)), 0, CARAVAN_PAGE_SLOTS);
        }
        return occupied;
    }

    private int countOccupied(HorseRecord record, int offset, int length) {
        ensureCargoSize(record);
        int occupied = 0;
        int end = Math.min(record.cargo().size(), offset + length);
        for (int i = offset; i < end; i++) {
            ItemStack item = record.cargo().get(i);
            if (item != null && !item.getType().isAir()) occupied++;
        }
        return occupied;
    }

    private boolean legitimateHandler(Horse horse) {
        if (horse.getPassengers().stream().anyMatch(Player.class::isInstance)) return true;
        return leashHolder(horse) instanceof Player;
    }

    private boolean flushPendingRewards(HorseRecord record) {
        if (record.pendingRewardGold() <= 0) return false;
        ensureCargoSize(record);
        long pending = record.pendingRewardGold();
        for (ItemStack item : record.cargo()) {
            if (pending <= 0) break;
            if (item != null && item.getType() == Material.GOLD_NUGGET && item.getAmount() < item.getMaxStackSize()) {
                int add = (int) Math.min(pending, item.getMaxStackSize() - item.getAmount());
                item.setAmount(item.getAmount() + add);
                pending -= add;
            }
        }
        for (int i = 0; i < record.cargo().size() && pending > 0; i++) {
            if (record.cargo().get(i) != null) continue;
            int add = (int) Math.min(64, pending);
            record.cargo().set(i, new ItemStack(Material.GOLD_NUGGET, add));
            pending -= add;
        }
        record.pendingRewardGold(pending);
        return true;
    }

    private void captureBaseSpeed(Horse horse, HorseRecord record) {
        AttributeInstance attribute = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (record.baseMovementSpeed() <= 0) record.baseMovementSpeed(attribute.getBaseValue());
    }

    private void applyCaravanSpeed(List<Horse> chain) {
        long gold = caravanGold(chain);
        for (Horse horse : chain) applySpeed(horse, record(horse), chain.size(), gold);
    }

    private void applySpeed(Horse horse, HorseRecord record, int chainSize, long caravanGold) {
        AttributeInstance attribute = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null || record.baseMovementSpeed() <= 0) return;
        double upgraded = caravanGold >= minimumCaravanGold
                ? record.baseMovementSpeed()
                : record.baseMovementSpeed() * (1.0 + multiplierPerTier * record.speedTier());
        double target = upgraded * caravanSpeedMultiplier(chainSize);
        if (Math.abs(attribute.getBaseValue() - target) > 0.000001) attribute.setBaseValue(target);
    }

    private double caravanSpeedMultiplier(int chainSize) {
        double multiplier = 1.0 - speedPenaltyPerAdditionalHorse * Math.max(0, chainSize - 1);
        return Math.max(minimumCaravanSpeedMultiplier, multiplier);
    }

    private Horse heldCaravanRoot(Player player, Horse target) {
        return player.getNearbyEntities(16.0, 16.0, 16.0).stream()
                .filter(Horse.class::isInstance)
                .map(Horse.class::cast)
                .filter(horse -> !horse.getUniqueId().equals(target.getUniqueId()))
                .filter(horse -> leashHolder(horse) == player)
                .filter(horse -> record(horse).cargoAttached())
                .min(Comparator.comparingDouble(horse -> horse.getLocation().distanceSquared(target.getLocation())))
                .orElse(null);
    }

    private Horse chainRoot(Horse horse) {
        Horse current = horse;
        Set<UUID> seen = new HashSet<>();
        while (seen.add(current.getUniqueId())) {
            Entity holder = leashHolder(current);
            if (!(holder instanceof Horse parent)) break;
            current = parent;
        }
        return current;
    }

    private List<Horse> caravanChain(Horse root) {
        List<Horse> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Horse current = root;
        while (current != null && seen.add(current.getUniqueId()) && chain.size() < maximumCaravanHorses) {
            chain.add(current);
            current = directFollower(current, seen);
        }
        return chain;
    }

    private Horse directFollower(Horse holder, Set<UUID> excluded) {
        return holder.getNearbyEntities(24.0, 24.0, 24.0).stream()
                .filter(Horse.class::isInstance)
                .map(Horse.class::cast)
                .filter(horse -> !excluded.contains(horse.getUniqueId()))
                .filter(horse -> leashHolder(horse) == holder)
                .min(Comparator.comparing(horse -> horse.getUniqueId().toString()))
                .orElse(null);
    }

    private Entity leashHolder(Horse horse) {
        if (!horse.isLeashed()) return null;
        try {
            return horse.getLeashHolder();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private boolean isOwner(Player player, Horse horse, HorseRecord record) {
        UUID ownerId = horse.getOwnerUniqueId();
        if (ownerId != null) record.ownerId(ownerId);
        return player.getUniqueId().equals(record.ownerId());
    }

    private Horse targetHorse(Player player) {
        Entity target = player.getTargetEntity(6);
        return target instanceof Horse horse ? horse : null;
    }

    private String nameOf(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private ItemStack menuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        item.setItemMeta(meta);
        return item;
    }

    private static final class CargoSession {
        private final UUID playerId;
        private final UUID rootHorseId;
        private final List<UUID> chainHorseIds;
        private Inventory inventory;
        private int pageIndex;
        private boolean switchingPages;

        private CargoSession(UUID playerId, UUID rootHorseId, List<UUID> chainHorseIds) {
            this.playerId = playerId;
            this.rootHorseId = rootHorseId;
            this.chainHorseIds = List.copyOf(chainHorseIds);
        }
    }
}
