package com.tiyler.roadstransport.data;

import com.tiyler.roadstransport.model.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DataManager {
    private final JavaPlugin plugin;
    private final File waypointsFile;
    private final File homesFile;
    private final File mailFile;
    private final File horsesFile;

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        waypointsFile = new File(folder, "waypoints.yml");
        homesFile = new File(folder, "homes.yml");
        mailFile = new File(folder, "mail.yml");
        horsesFile = new File(folder, "horses.yml");
    }

    public Map<UUID, WaypointRecord> loadWaypoints() {
        Map<UUID, WaypointRecord> result = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(waypointsFile);
        ConfigurationSection root = yaml.getConfigurationSection("waypoints");
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                WaypointType type = WaypointType.valueOf(s.getString("type", "PUBLIC"));
                String name = s.getString("name", "Waypoint");
                BlockKey block = BlockKey.deserialize(Objects.requireNonNull(s.getString("block")));
                UUID creator = UUID.fromString(Objects.requireNonNull(s.getString("creator")));
                UUID owner = UUID.fromString(Objects.requireNonNull(s.getString("owner")));
                UUID claimId = nullableUuid(s.getString("claim-id"));
                String claimName = s.getString("claim-name");
                WaypointRecord record = new WaypointRecord(id, type, name, block, creator, owner, claimId, claimName);
                record.storedGold(s.getLong("stored-gold", 0));
                for (String value : s.getStringList("authorized")) record.authorized().add(UUID.fromString(value));
                result.put(id, record);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid waypoint " + key + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public void saveWaypoints(Collection<WaypointRecord> records) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (WaypointRecord record : records) {
            String p = "waypoints." + record.id();
            yaml.set(p + ".type", record.type().name());
            yaml.set(p + ".name", record.name());
            yaml.set(p + ".block", record.block().serialize());
            yaml.set(p + ".creator", record.creatorId().toString());
            yaml.set(p + ".owner", record.ownerId().toString());
            yaml.set(p + ".claim-id", record.claimId() == null ? null : record.claimId().toString());
            yaml.set(p + ".claim-name", record.claimName());
            yaml.set(p + ".stored-gold", record.storedGold());
            yaml.set(p + ".authorized", record.authorized().stream().map(UUID::toString).toList());
        }
        save(yaml, waypointsFile);
    }

    public Map<UUID, Map<String, HomeRecord>> loadHomes() {
        Map<UUID, Map<String, HomeRecord>> result = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(homesFile);
        ConfigurationSection root = yaml.getConfigurationSection("homes");
        if (root == null) return result;
        for (String playerKey : root.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(playerKey);
                ConfigurationSection player = root.getConfigurationSection(playerKey);
                if (player == null) continue;
                Map<String, HomeRecord> homes = new HashMap<>();
                for (String key : player.getKeys(false)) {
                    ConfigurationSection s = player.getConfigurationSection(key);
                    if (s == null) continue;
                    String name = s.getString("name", key);
                    BlockKey location = BlockKey.deserialize(Objects.requireNonNull(s.getString("location")));
                    UUID claimId = nullableUuid(s.getString("generated-claim-id"));
                    homes.put(normalizeName(name), new HomeRecord(playerId, name, location, claimId));
                }
                result.put(playerId, homes);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid homes for " + playerKey + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public void saveHomes(Map<UUID, Map<String, HomeRecord>> homes) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var playerEntry : homes.entrySet()) {
            for (HomeRecord home : playerEntry.getValue().values()) {
                String key = safeKey(home.name());
                String p = "homes." + playerEntry.getKey() + "." + key;
                yaml.set(p + ".name", home.name());
                yaml.set(p + ".location", home.location().serialize());
                yaml.set(p + ".generated-claim-id", home.generatedClaimId() == null ? null : home.generatedClaimId().toString());
            }
        }
        save(yaml, homesFile);
    }

    public Map<UUID, MailArea> loadMailAreas() {
        Map<UUID, MailArea> result = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mailFile);
        ConfigurationSection root = yaml.getConfigurationSection("areas");
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                ConfigurationSection area = root.getConfigurationSection(key);
                if (area == null) {
                    // Backward compatibility with 0.1.0-alpha, which stored only the center string.
                    String legacyCenter = root.getString(key);
                    if (legacyCenter == null) continue;
                    result.put(owner, new MailArea(owner, BlockKey.deserialize(legacyCenter), "Mailbox", null));
                    continue;
                }
                BlockKey center = BlockKey.deserialize(Objects.requireNonNull(area.getString("center")));
                String name = area.getString("name", "Mailbox");
                String signValue = area.getString("sign-block");
                BlockKey signBlock = signValue == null ? null : BlockKey.deserialize(signValue);
                result.put(owner, new MailArea(owner, center, name, signBlock));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid mail area " + key + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public Map<UUID, Shipment> loadShipments() {
        Map<UUID, Shipment> result = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mailFile);
        ConfigurationSection root = yaml.getConfigurationSection("shipments");
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                UUID sender = UUID.fromString(Objects.requireNonNull(s.getString("sender")));
                UUID recipient = UUID.fromString(Objects.requireNonNull(s.getString("recipient")));
                String senderName = s.getString("sender-name", sender.toString());
                String claimName = s.getString("sender-claim-name");
                long sentAt = s.getLong("sent-at");
                long dueAt = s.getLong("due-at");
                boolean rush = s.getBoolean("rush");
                ShipmentStatus status = ShipmentStatus.valueOf(s.getString("status", "IN_TRANSIT"));
                String blockData = s.getString("barrel-block-data", "minecraft:barrel[facing=north,open=false]");
                List<ItemStack> contents = itemList(s.getList("contents"));
                Shipment shipment = new Shipment(id, sender, recipient, senderName, claimName, sentAt, dueAt,
                        rush, status, blockData, contents);
                String delivered = s.getString("delivered-block");
                if (delivered != null) shipment.deliveredBlock(BlockKey.deserialize(delivered));
                shipment.deliveredToId(nullableUuid(s.getString("delivered-to")));
                result.put(id, shipment);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid shipment " + key + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public void saveMail(Collection<MailArea> areas, Collection<Shipment> shipments) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (MailArea area : areas) {
            String p = "areas." + area.ownerId();
            yaml.set(p + ".center", area.center().serialize());
            yaml.set(p + ".name", area.name());
            yaml.set(p + ".sign-block", area.signBlock() == null ? null : area.signBlock().serialize());
        }
        for (Shipment shipment : shipments) {
            String p = "shipments." + shipment.id();
            yaml.set(p + ".sender", shipment.senderId().toString());
            yaml.set(p + ".recipient", shipment.recipientId().toString());
            yaml.set(p + ".sender-name", shipment.senderName());
            yaml.set(p + ".sender-claim-name", shipment.senderClaimName());
            yaml.set(p + ".sent-at", shipment.sentAtMillis());
            yaml.set(p + ".due-at", shipment.dueAtMillis());
            yaml.set(p + ".rush", shipment.rush());
            yaml.set(p + ".status", shipment.status().name());
            yaml.set(p + ".barrel-block-data", shipment.barrelBlockData());
            yaml.set(p + ".contents", shipment.contents());
            yaml.set(p + ".delivered-block", shipment.deliveredBlock() == null ? null : shipment.deliveredBlock().serialize());
            yaml.set(p + ".delivered-to", shipment.deliveredToId() == null ? null : shipment.deliveredToId().toString());
        }
        save(yaml, mailFile);
    }

    public Map<UUID, HorseRecord> loadHorses() {
        Map<UUID, HorseRecord> result = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(horsesFile);
        ConfigurationSection root = yaml.getConfigurationSection("horses");
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID id = UUID.fromString(key);
                HorseRecord record = new HorseRecord(id);
                record.ownerId(nullableUuid(s.getString("owner")));
                record.speedTier(s.getInt("speed-tier", 0));
                record.enchantedAppleUpgradeApplied(s.getBoolean("enchanted-apple-upgrade-applied", false));
                record.baseMovementSpeed(s.getDouble("base-speed", 0.0));
                record.cargoAttached(s.getBoolean("cargo-attached", false));
                List<ItemStack> cargo = itemList(s.getList("cargo"));
                for (int i = 0; i < Math.min(record.cargo().size(), cargo.size()); i++) {
                    record.cargo().set(i, cargo.get(i) == null ? null : cargo.get(i).clone());
                }
                for (String value : s.getStringList("trusted")) record.trusted().add(UUID.fromString(value));
                for (String value : s.getStringList("visited-chunks")) record.visitedChunks().add(ChunkKey.deserialize(value));
                record.partialChunkProgress(s.getInt("partial-progress", 0));
                record.pendingRewardGold(s.getLong("pending-reward", 0));
                result.put(id, record);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid horse " + key + ": " + ex.getMessage());
            }
        }
        return result;
    }

    public void saveHorses(Collection<HorseRecord> records) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (HorseRecord record : records) {
            String p = "horses." + record.horseId();
            yaml.set(p + ".owner", record.ownerId() == null ? null : record.ownerId().toString());
            yaml.set(p + ".speed-tier", record.speedTier());
            yaml.set(p + ".enchanted-apple-upgrade-applied", record.enchantedAppleUpgradeApplied());
            yaml.set(p + ".base-speed", record.baseMovementSpeed());
            yaml.set(p + ".cargo-attached", record.cargoAttached());
            yaml.set(p + ".cargo", record.cargo());
            yaml.set(p + ".trusted", record.trusted().stream().map(UUID::toString).toList());
            yaml.set(p + ".visited-chunks", record.visitedChunks().stream().map(ChunkKey::serialize).toList());
            yaml.set(p + ".partial-progress", record.partialChunkProgress());
            yaml.set(p + ".pending-reward", record.pendingRewardGold());
        }
        save(yaml, horsesFile);
    }

    private List<ItemStack> itemList(List<?> raw) {
        List<ItemStack> result = new ArrayList<>();
        if (raw == null) return result;
        for (Object value : raw) result.add(value instanceof ItemStack item ? item.clone() : null);
        return result;
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
