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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class HorseService {
    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private final Map<UUID, HorseRecord> horses;
    private final Map<UUID, ChunkKey> lastChunks = new HashMap<>();
    private final Map<UUID, Inventory> openCargo = new HashMap<>();
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
    }

    public void save() {
        dataManager.saveHorses(horses.values());
    }

    public void shutdown() {
        for (var entry : new ArrayList<>(openCargo.entrySet())) {
            HorseRecord record = horses.get(entry.getKey());
            if (record != null) copyInventoryToRecord(entry.getValue(), record);
            for (var viewer : new ArrayList<>(entry.getValue().getViewers())) viewer.closeInventory();
        }
        openCargo.clear();
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

    public void handleBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Horse child)
                || !(event.getMother() instanceof Horse mother)
                || !(event.getFather() instanceof Horse father)) return;
        HorseRecord childRecord = record(child);
        int inherited = 0;
        HorseRecord motherRecord = horses.get(mother.getUniqueId());
        HorseRecord fatherRecord = horses.get(father.getUniqueId());
        if (motherRecord != null && motherRecord.speedTier() > 0 && ThreadLocalRandom.current().nextDouble() < inheritanceChance) {
            inherited = Math.max(inherited, motherRecord.speedTier());
        }
        if (fatherRecord != null && fatherRecord.speedTier() > 0 && ThreadLocalRandom.current().nextDouble() < inheritanceChance) {
            inherited = Math.max(inherited, fatherRecord.speedTier());
        }
        childRecord.speedTier(Math.min(maximumTier, inherited));
        captureBaseSpeed(child, childRecord);
        applySpeed(child, childRecord);
        save();
    }

    public void handleTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        Location destination = event.getTo();
        if (destination == null) {
            lastChunks.remove(horse.getUniqueId());
            return;
        }
        ChunkKey key = ChunkKey.of(destination);
        lastChunks.put(horse.getUniqueId(), key);
        HorseRecord record = horses.get(horse.getUniqueId());
        if (record != null && cargoGold(record) > 0) record.visitedChunks().add(key);
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof HorseCargoHolder holder)) return;
        openCargo.remove(holder.horseId(), event.getInventory());
        HorseRecord record = horses.get(holder.horseId());
        if (record == null) return;
        copyInventoryToRecord(event.getInventory(), record);
        save();
    }

    public void handleDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        HorseRecord record = horses.get(horse.getUniqueId());
        if (record == null) return;
        Inventory open = openCargo.remove(horse.getUniqueId());
        if (open != null) {
            copyInventoryToRecord(open, record);
            for (var viewer : new ArrayList<>(open.getViewers())) viewer.closeInventory();
        }
        horses.remove(horse.getUniqueId());
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
        long gold = cargoGold(record);
        int occupied = (int) record.cargo().stream().filter(item -> item != null && !item.getType().isAir()).count();
        Messages.info(player, "Horse speed tier: " + record.speedTier() + "/" + maximumTier + ".");
        Messages.info(player, "Enchanted-apple speed increase: "
                + (record.enchantedAppleUpgradeApplied() ? "already used" : "available") + ".");
        Messages.info(player, "Cargo: " + (record.cargoAttached() ? occupied + "/" + cargoSlots + " slots" : "not attached") + ".");
        Messages.info(player, "Cargo gold: " + gold + "g. Enhanced speed is " + (gold > 0 ? "suppressed" : "active") + ".");
        if (gold >= minimumCaravanGold) {
            int remaining = rewardEveryChunks - record.partialChunkProgress();
            Messages.info(player, "Caravan progress: " + record.partialChunkProgress() + "/" + rewardEveryChunks
                    + " unique chunks; " + remaining + " until the next 1g reward.");
        }
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
        for (HorseRecord record : horses.values()) {
            Entity entity = Bukkit.getEntity(record.horseId());
            if (!(entity instanceof Horse horse) || !horse.isValid()) continue;
            captureBaseSpeed(horse, record);
            long gold = cargoGold(record);
            if (gold == 0) {
                if (!record.visitedChunks().isEmpty() || record.partialChunkProgress() != 0) {
                    record.clearJourney();
                    changed = true;
                }
                applySpeed(horse, record);
                lastChunks.put(horse.getUniqueId(), ChunkKey.of(horse.getLocation()));
                continue;
            }
            applySpeed(horse, record);
            if (gold < minimumCaravanGold || !legitimateHandler(horse)) {
                lastChunks.put(horse.getUniqueId(), ChunkKey.of(horse.getLocation()));
                continue;
            }
            ChunkKey current = ChunkKey.of(horse.getLocation());
            ChunkKey previous = lastChunks.put(horse.getUniqueId(), current);
            if (previous == null) {
                record.visitedChunks().add(current);
                changed = true;
                continue;
            }
            if (current.equals(previous)) continue;
            if (!current.adjacentOrSame(previous, maximumChunkStep)) continue;
            if (!record.visitedChunks().add(current)) continue;
            record.partialChunkProgress(record.partialChunkProgress() + 1);
            changed = true;
            while (record.partialChunkProgress() >= rewardEveryChunks) {
                record.partialChunkProgress(record.partialChunkProgress() - rewardEveryChunks);
                record.pendingRewardGold(record.pendingRewardGold() + 1);
            }
            if (flushPendingRewards(record)) changed = true;
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
        return record;
    }

    private void upgradeHorse(Player player, Horse horse, HorseRecord record, ItemStack held) {
        if (!isOwner(player, horse, record) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "Only the horse's owner may permanently improve it.");
            return;
        }
        if (record.enchantedAppleUpgradeApplied()) {
            Messages.error(player, "This horse has already gained its one enchanted-apple speed increase.");
            return;
        }
        if (record.speedTier() >= maximumTier) {
            Messages.error(player, "This horse has already reached Speed " + maximumTier + ".");
            return;
        }
        held.setAmount(held.getAmount() - 1);
        if (ThreadLocalRandom.current().nextDouble() >= enchantedAppleSuccessChance) {
            Messages.info(player, "The enchanted golden apple has no lasting effect.");
            return;
        }
        record.speedTier(record.speedTier() + 1);
        record.enchantedAppleUpgradeApplied(true);
        applySpeed(horse, record);
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
        Messages.success(player, "Attached a 54-slot cargo inventory to the armored horse. Sneak-right-click it with an empty hand to open cargo.");
    }

    private void openCargo(Player player, Horse horse, HorseRecord record) {
        if (!record.mayAccess(player.getUniqueId()) && !player.hasPermission("roadsandtransport.admin")) {
            Messages.error(player, "You do not have permission to open this horse's cargo.");
            return;
        }
        Inventory existing = openCargo.get(horse.getUniqueId());
        if (existing != null && !existing.getViewers().isEmpty()) {
            Messages.error(player, "Someone else is already using this horse's cargo.");
            return;
        }
        HorseCargoHolder holder = new HorseCargoHolder(horse.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, cargoSlots,
                Component.text("Horse Cargo", NamedTextColor.GOLD));
        holder.inventory(inventory);
        ItemStack[] contents = new ItemStack[cargoSlots];
        for (int i = 0; i < Math.min(cargoSlots, record.cargo().size()); i++) {
            ItemStack item = record.cargo().get(i);
            contents[i] = item == null ? null : item.clone();
        }
        inventory.setContents(contents);
        openCargo.put(horse.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    private void copyInventoryToRecord(Inventory inventory, HorseRecord record) {
        ensureCargoSize(record);
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < record.cargo().size(); i++) {
            record.cargo().set(i, i < contents.length && contents[i] != null ? contents[i].clone() : null);
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

    private boolean legitimateHandler(Horse horse) {
        if (horse.getPassengers().stream().anyMatch(Player.class::isInstance)) return true;
        if (!horse.isLeashed()) return false;
        try {
            return horse.getLeashHolder() instanceof Player;
        } catch (IllegalStateException ignored) {
            return false;
        }
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

    private void applySpeed(Horse horse, HorseRecord record) {
        AttributeInstance attribute = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null || record.baseMovementSpeed() <= 0) return;
        double target = cargoGold(record) > 0
                ? record.baseMovementSpeed()
                : record.baseMovementSpeed() * (1.0 + multiplierPerTier * record.speedTier());
        if (Math.abs(attribute.getBaseValue() - target) > 0.000001) attribute.setBaseValue(target);
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
}
