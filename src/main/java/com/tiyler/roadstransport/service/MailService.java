package com.tiyler.roadstransport.service;

import com.tiyler.roadstransport.bridge.KingdomsBridge;
import com.tiyler.roadstransport.data.DataManager;
import com.tiyler.roadstransport.model.*;
import com.tiyler.roadstransport.util.Messages;
import com.tiyler.roadstransport.util.WorldUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class MailService {
    private final JavaPlugin plugin;
    private final DataManager dataManager;
    private final KingdomsBridge kingdoms;
    private final Map<UUID, MailArea> areas;
    private final Map<UUID, Shipment> shipments;
    private final Map<BlockKey, UUID> deliveredByBlock = new HashMap<>();
    private final Map<UUID, UUID> labelByShipment = new HashMap<>();
    private final NamespacedKey labelKey;
    private final NamespacedKey shipmentKey;
    private final NamespacedKey mailboxSignKey;
    private final NamespacedKey mailboxOwnerKey;
    private final int normalCost;
    private final int rushBaseCost;
    private final int rushPerSlot;
    private final long normalDelayMillis;
    private final int deliveryRadius;

    public MailService(JavaPlugin plugin, DataManager dataManager, KingdomsBridge kingdoms) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.kingdoms = kingdoms;
        areas = dataManager.loadMailAreas();
        shipments = dataManager.loadShipments();
        for (Shipment shipment : shipments.values()) {
            if (shipment.deliveredBlock() != null && shipment.status() == ShipmentStatus.DELIVERED) {
                deliveredByBlock.put(shipment.deliveredBlock(), shipment.id());
            }
        }
        labelKey = new NamespacedKey(plugin, "mail_crate_label");
        shipmentKey = new NamespacedKey(plugin, "mail_shipment_id");
        mailboxSignKey = new NamespacedKey(plugin, "mailbox_area_sign");
        mailboxOwnerKey = new NamespacedKey(plugin, "mailbox_area_owner");
        normalCost = plugin.getConfig().getInt("mail.normal-cost", 5);
        rushBaseCost = plugin.getConfig().getInt("mail.rush-base-cost", 10);
        rushPerSlot = plugin.getConfig().getInt("mail.rush-cost-per-occupied-slot", 1);
        long delayTicks = plugin.getConfig().getLong("mail.delivery-delay-ticks", 24000L);
        normalDelayMillis = delayTicks * 50L;
        deliveryRadius = plugin.getConfig().getInt("mail.delivery-radius", 5);
    }

    public void save() {
        dataManager.saveMail(areas.values(), shipments.values());
    }

    public void createArea(Player player, String name) {
        createAreaFor(player, player, name, false);
    }

    public void createAreaFor(Player creator, OfflinePlayer owner, String name) {
        if (!creator.hasPermission("roadsandtransport.admin")) {
            Messages.error(creator, "You do not have permission to create another player's mail area.");
            return;
        }
        createAreaFor(creator, owner, name, true);
    }

    private void createAreaFor(Player creator, OfflinePlayer owner, String name, boolean targeted) {
        if (owner == null || owner.getName() == null) {
            Messages.error(creator, "That player could not be found.");
            return;
        }
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank() || clean.length() > 32) {
            Messages.error(creator, "Mailbox names must be between 1 and 32 characters.");
            return;
        }
        if (!kingdoms.canBuild(creator, creator.getLocation())) {
            Messages.error(creator, "You may only create a mail delivery area where you are allowed to build.");
            return;
        }

        MailArea existing = areas.get(owner.getUniqueId());
        if (existing != null) removeMailboxSign(existing);

        Location centerLocation = creator.getLocation().getBlock().getLocation();
        Location signLocation = findMailboxSignPlacement(centerLocation);
        if (signLocation == null) {
            if (existing != null) ensureMailboxSign(existing);
            Messages.error(creator, "No open ground was found at the edge of the five-block mail area for its sign.");
            return;
        }

        BlockKey center = BlockKey.of(centerLocation);
        BlockKey signBlock = BlockKey.of(signLocation);
        MailArea area = new MailArea(owner.getUniqueId(), center, clean, signBlock);
        areas.put(owner.getUniqueId(), area);
        placeMailboxSign(area, owner.getName());
        save();

        if (targeted) {
            Messages.success(creator, "Created " + owner.getName() + "'s mailbox area, " + clean + ".");
            Player onlineOwner = owner.getPlayer();
            if (onlineOwner != null && !onlineOwner.getUniqueId().equals(creator.getUniqueId())) {
                Messages.info(onlineOwner, creator.getName() + " created your mailbox area, " + clean + ".");
            }
        } else {
            Messages.success(creator, "Created mailbox area " + clean + ".");
        }
    }

    public void removeArea(Player player) {
        MailArea removed = areas.remove(player.getUniqueId());
        if (removed == null) {
            Messages.error(player, "You do not have a mail delivery area.");
            return;
        }
        removeMailboxSign(removed);
        save();
        Messages.success(player, "Removed your mail delivery area. Undelivered crates will return to their senders.");
    }

    public void areaInfo(Player player) {
        MailArea area = areas.get(player.getUniqueId());
        if (area == null) {
            Messages.error(player, "You do not have a mail delivery area.");
            return;
        }
        Messages.info(player, "Mailbox " + area.name() + " is centered at " + area.center().x() + ", " + area.center().y()
                + ", " + area.center().z() + " with a radius of " + deliveryRadius + " blocks.");
    }

    public boolean isMailboxSign(Block block) {
        if (!(block.getState() instanceof Sign sign)) return false;
        return sign.getPersistentDataContainer().has(mailboxSignKey, PersistentDataType.BYTE);
    }

    public boolean canManageMailboxSign(Player player, Block block) {
        if (!(block.getState() instanceof Sign sign)) return true;
        String ownerValue = sign.getPersistentDataContainer().get(mailboxOwnerKey, PersistentDataType.STRING);
        if (ownerValue == null) return true;
        return player.hasPermission("roadsandtransport.admin") || player.getUniqueId().toString().equals(ownerValue);
    }

    public void ensureAreaSigns() {
        boolean changed = false;
        for (MailArea area : new ArrayList<>(areas.values())) {
            BlockKey existingKey = area.signBlock();
            Location existingLocation = existingKey == null ? null : existingKey.location();
            if (existingLocation != null && isMailboxSign(existingLocation.getBlock())) continue;

            Location center = area.center().location();
            if (center == null) continue;
            Location replacement = findMailboxSignPlacement(center);
            if (replacement == null) continue;
            MailArea updated = new MailArea(area.ownerId(), area.center(), area.name(), BlockKey.of(replacement));
            areas.put(area.ownerId(), updated);
            String ownerName = nameOf(area.ownerId());
            placeMailboxSign(updated, ownerName);
            changed = true;
        }
        if (changed) save();
    }

    public void send(Player sender, OfflinePlayer recipient, boolean rush) {
        if (recipient == null || recipient.getName() == null) {
            Messages.error(sender, "That player could not be found.");
            return;
        }
        if (recipient.getUniqueId().equals(sender.getUniqueId())) {
            Messages.error(sender, "You cannot mail a crate to yourself.");
            return;
        }
        MailArea destinationArea = areas.get(recipient.getUniqueId());
        if (destinationArea == null) {
            Messages.error(sender, recipient.getName() + " has not created a mail delivery area.");
            return;
        }
        Block block = sender.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Barrel barrel)) {
            Messages.error(sender, "Look directly at the barrel you want to send.");
            return;
        }
        if (deliveredByBlock.containsKey(BlockKey.of(block))) {
            Messages.error(sender, "Open this delivered crate before sending it again.");
            return;
        }
        if (!kingdoms.canBuild(sender, block.getLocation())) {
            Messages.error(sender, "You may only send a barrel you are allowed to remove.");
            return;
        }
        List<ItemStack> contents = Arrays.stream(barrel.getInventory().getContents())
                .map(item -> item == null ? null : item.clone()).toList();
        int occupied = recursiveOccupiedSlots(contents);
        long cost = rush ? (long) rushBaseCost + (long) rushPerSlot * occupied : normalCost;
        Location immediatePlacement = null;
        if (rush) {
            Location center = destinationArea.center().location();
            immediatePlacement = center == null ? null : WorldUtil.findSafeBarrelPlacement(center, deliveryRadius);
            if (immediatePlacement == null) {
                Messages.error(sender, "No safe space is currently available in the recipient's mail area.");
                return;
            }
        }
        if (!kingdoms.withdrawPurse(sender.getUniqueId(), cost)) {
            Messages.error(sender, "You need " + cost + "g in your purse.");
            return;
        }
        String blockData = block.getBlockData().getAsString();
        long now = System.currentTimeMillis();
        Shipment shipment = new Shipment(UUID.randomUUID(), sender.getUniqueId(), recipient.getUniqueId(),
                sender.getName(), kingdoms.realmName(sender.getUniqueId()), now,
                rush ? now : now + normalDelayMillis, rush, ShipmentStatus.IN_TRANSIT, blockData, contents);
        shipments.put(shipment.id(), shipment);
        barrel.getSnapshotInventory().clear();
        barrel.update(true, false);
        block.setType(Material.AIR, false);
        save();
        if (rush) {
            if (!placeShipment(shipment, immediatePlacement, false)) {
                shipment.status(ShipmentStatus.WAITING_FOR_SPACE);
                save();
                Messages.info(sender, "The rush crate entered the delivery queue because placement changed unexpectedly.");
            } else {
                Messages.success(sender, "Rush-delivered the crate to " + recipient.getName() + " for " + cost + "g.");
            }
        } else {
            Messages.success(sender, "Sent the crate to " + recipient.getName() + " for " + cost
                    + "g. It will arrive in one Minecraft day.");
        }
    }

    public void status(Player player) {
        List<Shipment> relevant = shipments.values().stream()
                .filter(s -> s.senderId().equals(player.getUniqueId()) || s.recipientId().equals(player.getUniqueId()))
                .sorted(Comparator.comparingLong(Shipment::sentAtMillis).reversed())
                .limit(12)
                .toList();
        if (relevant.isEmpty()) {
            Messages.info(player, "You have no recorded mail shipments.");
            return;
        }
        Messages.info(player, "Recent mail shipments:");
        for (Shipment shipment : relevant) {
            boolean outgoing = shipment.senderId().equals(player.getUniqueId());
            String other = outgoing ? nameOf(shipment.recipientId()) : shipment.senderName();
            String direction = outgoing ? "to " : "from ";
            Messages.info(player, "- " + direction + other + ": " + readableStatus(shipment));
        }
    }

    public void processDueShipments() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Shipment shipment : shipments.values()) {
            if (shipment.status() == ShipmentStatus.DELIVERED) continue;
            if (shipment.status() == ShipmentStatus.IN_TRANSIT && shipment.dueAtMillis() > now) continue;
            boolean returning = shipment.status() == ShipmentStatus.RETURNING
                    || shipment.status() == ShipmentStatus.WAITING_FOR_RETURN_SPACE;
            UUID target = returning ? shipment.senderId() : shipment.recipientId();
            MailArea area = areas.get(target);
            if (area == null) {
                if (!returning) {
                    shipment.status(ShipmentStatus.RETURNING);
                } else {
                    shipment.status(ShipmentStatus.WAITING_FOR_RETURN_SPACE);
                }
                changed = true;
                continue;
            }
            Location center = area.center().location();
            Location placement = center == null ? null : WorldUtil.findSafeBarrelPlacement(center, deliveryRadius);
            if (placement == null) {
                shipment.status(returning ? ShipmentStatus.WAITING_FOR_RETURN_SPACE : ShipmentStatus.WAITING_FOR_SPACE);
                changed = true;
                continue;
            }
            if (placeShipment(shipment, placement, returning)) changed = true;
        }
        if (changed) save();
    }

    public boolean isDeliveredCrate(Block block) {
        return deliveredByBlock.containsKey(BlockKey.of(block));
    }

    public boolean canOpenDeliveredCrate(Player player, Block block) {
        Shipment shipment = shipmentAt(block);
        UUID deliveredTo = shipment == null ? null : shipment.deliveredToId();
        return shipment == null || (deliveredTo == null ? shipment.recipientId() : deliveredTo).equals(player.getUniqueId())
                || player.hasPermission("roadsandtransport.admin");
    }

    public void openedDeliveredCrate(Player player, Block block) {
        Shipment shipment = shipmentAt(block);
        if (shipment == null) return;
        removeLabel(shipment.id());
        deliveredByBlock.remove(BlockKey.of(block));
        shipment.deliveredBlock(null);
        shipment.status(ShipmentStatus.DELIVERED);
        save();
        boolean returned = shipment.deliveredToId() != null
                && shipment.deliveredToId().equals(shipment.senderId())
                && !shipment.senderId().equals(shipment.recipientId());
        Messages.success(player, returned
                ? "You opened your returned mail crate. It is now an ordinary barrel."
                : "You opened a crate from " + shipment.senderName() + ". It is now an ordinary barrel.");
    }

    public void brokenDeliveredCrate(Player player, Block block) {
        Shipment shipment = shipmentAt(block);
        if (shipment == null) return;
        removeLabel(shipment.id());
        deliveredByBlock.remove(BlockKey.of(block));
        shipment.deliveredBlock(null);
        shipment.status(ShipmentStatus.DELIVERED);
        save();
    }

    public void ensureLabels() {
        for (Shipment shipment : shipments.values()) {
            if (shipment.deliveredBlock() != null && deliveredByBlock.containsKey(shipment.deliveredBlock())) {
                restoreMissingDeliveredContents(shipment);
                ensureLabel(shipment);
            }
        }
    }

    public void removeAllLabels() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(labelKey, PersistentDataType.BYTE)) display.remove();
            }
        }
        labelByShipment.clear();
    }

    private boolean placeShipment(Shipment shipment, Location placement, boolean returning) {
        Block block = placement.getBlock();
        if (!block.getType().isAir()) return false;
        try {
            BlockData data = Bukkit.createBlockData(shipment.barrelBlockData());
            block.setBlockData(data, false);
        } catch (IllegalArgumentException ex) {
            block.setType(Material.BARREL, false);
        }
        if (!(block.getState() instanceof Barrel barrel)) {
            block.setType(Material.AIR, false);
            return false;
        }
        ItemStack[] deliveredContents = normalizeBarrelContents(shipment.contents());
        // Container BlockStates are snapshots. Populate the snapshot first, then apply it to the world.
        // Writing to the live inventory and calling update() afterwards can overwrite it with the
        // empty captured snapshot, which was causing delivered barrels to arrive without their items.
        barrel.getSnapshotInventory().setContents(cloneContents(deliveredContents));
        if (!barrel.update(true, false)) {
            block.setType(Material.AIR, false);
            return false;
        }
        // Reapply to the live inventory as a defensive Paper compatibility check.
        if (!(block.getState() instanceof Barrel placedBarrel)) {
            block.setType(Material.AIR, false);
            return false;
        }
        placedBarrel.getInventory().setContents(cloneContents(deliveredContents));
        BlockKey key = BlockKey.of(block);
        shipment.deliveredBlock(key);
        shipment.status(ShipmentStatus.DELIVERED);
        UUID deliveredTo = returning ? shipment.senderId() : shipment.recipientId();
        shipment.deliveredToId(deliveredTo);
        deliveredByBlock.put(key, shipment.id());
        ensureLabel(shipment);
        UUID notified = deliveredTo;
        Player online = Bukkit.getPlayer(notified);
        if (online != null) {
            Messages.success(online, returning ? "A returned mail crate has arrived." : "Mail has arrived in your delivery area.");
        }
        return true;
    }

    private void restoreMissingDeliveredContents(Shipment shipment) {
        BlockKey key = shipment.deliveredBlock();
        Location location = key == null ? null : key.location();
        if (location == null || !(location.getBlock().getState() instanceof Barrel barrel)) return;
        if (!barrel.getInventory().isEmpty()) return;
        ItemStack[] expected = normalizeBarrelContents(shipment.contents());
        boolean hasExpectedItems = Arrays.stream(expected).anyMatch(item -> item != null && !item.getType().isAir());
        if (!hasExpectedItems) return;
        barrel.getInventory().setContents(cloneContents(expected));
        plugin.getLogger().info("Restored missing contents for delivered mail shipment " + shipment.id() + ".");
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private void ensureLabel(Shipment shipment) {
        BlockKey key = shipment.deliveredBlock();
        Location blockLocation = key == null ? null : key.location();
        if (blockLocation == null) return;
        int chunkX = key.x() >> 4;
        int chunkZ = key.z() >> 4;
        if (!blockLocation.getWorld().isChunkLoaded(chunkX, chunkZ)
                || blockLocation.getBlock().getType() != Material.BARREL) return;
        UUID existing = labelByShipment.get(shipment.id());
        if (existing != null && Bukkit.getEntity(existing) instanceof TextDisplay display && display.isValid()) return;
        String label = "[Crate] from " + shipment.senderName();
        if (shipment.senderClaimName() != null && !shipment.senderClaimName().isBlank()) {
            label += " [" + shipment.senderClaimName() + "]";
        }
        String finalLabel = label;
        TextDisplay display = blockLocation.getWorld().spawn(blockLocation.clone().add(0.5, 1.5, 0.5), TextDisplay.class, text -> {
            text.text(Component.text(finalLabel, NamedTextColor.GOLD));
            text.setBillboard(Display.Billboard.CENTER);
            text.setSeeThrough(true);
            text.setShadowed(true);
            text.setPersistent(false);
            text.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
            text.getPersistentDataContainer().set(shipmentKey, PersistentDataType.STRING, shipment.id().toString());
        });
        labelByShipment.put(shipment.id(), display.getUniqueId());
    }

    private void removeLabel(UUID shipmentId) {
        UUID entityId = labelByShipment.remove(shipmentId);
        if (entityId != null && Bukkit.getEntity(entityId) != null) Bukkit.getEntity(entityId).remove();
    }

    private Shipment shipmentAt(Block block) {
        UUID id = deliveredByBlock.get(BlockKey.of(block));
        return id == null ? null : shipments.get(id);
    }

    private ItemStack[] normalizeBarrelContents(List<ItemStack> source) {
        ItemStack[] result = new ItemStack[27];
        for (int i = 0; i < Math.min(27, source.size()); i++) result[i] = source.get(i) == null ? null : source.get(i).clone();
        return result;
    }

    private int recursiveOccupiedSlots(Collection<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            total++;
            if (item.getItemMeta() instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof Container container) {
                total += recursiveOccupiedSlots(Arrays.asList(container.getInventory().getContents()));
            } else if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
                total += recursiveOccupiedSlots(bundleMeta.getItems());
            }
        }
        return total;
    }

    private Location findMailboxSignPlacement(Location center) {
        World world = center.getWorld();
        int baseY = center.getBlockY();
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[]{0, -deliveryRadius});
        offsets.add(new int[]{deliveryRadius, 0});
        offsets.add(new int[]{0, deliveryRadius});
        offsets.add(new int[]{-deliveryRadius, 0});
        for (int dx = -deliveryRadius; dx <= deliveryRadius; dx++) {
            for (int dz = -deliveryRadius; dz <= deliveryRadius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != deliveryRadius) continue;
                if ((dx == 0 && Math.abs(dz) == deliveryRadius) || (dz == 0 && Math.abs(dx) == deliveryRadius)) continue;
                offsets.add(new int[]{dx, dz});
            }
        }
        for (int[] offset : offsets) {
            for (int dy = -2; dy <= 2; dy++) {
                int y = baseY + dy;
                if (y <= world.getMinHeight() || y >= world.getMaxHeight()) continue;
                Block place = world.getBlockAt(center.getBlockX() + offset[0], y, center.getBlockZ() + offset[1]);
                Block floor = place.getRelative(0, -1, 0);
                if (!place.getType().isAir() || !floor.getType().isSolid()) continue;
                return place.getLocation();
            }
        }
        return null;
    }

    private void placeMailboxSign(MailArea area, String ownerName) {
        if (area.signBlock() == null) return;
        Location location = area.signBlock().location();
        if (location == null) return;
        Block block = location.getBlock();
        if (!block.getType().isAir() && !isMailboxSign(block)) return;
        block.setType(Material.OAK_SIGN, false);
        if (!(block.getState() instanceof Sign sign)) return;
        List<String> mailboxLines = splitMailboxName(area.name());
        var front = sign.getSide(Side.FRONT);
        front.line(0, Component.text(ownerName + "'s", NamedTextColor.DARK_GREEN));
        front.line(1, Component.text("Mailbox-", NamedTextColor.DARK_GREEN));
        front.line(2, Component.text(mailboxLines.get(0), NamedTextColor.BLACK));
        front.line(3, Component.text(mailboxLines.get(1), NamedTextColor.BLACK));
        var back = sign.getSide(Side.BACK);
        back.line(0, Component.text(ownerName + "'s", NamedTextColor.DARK_GREEN));
        back.line(1, Component.text("Mailbox-", NamedTextColor.DARK_GREEN));
        back.line(2, Component.text(mailboxLines.get(0), NamedTextColor.BLACK));
        back.line(3, Component.text(mailboxLines.get(1), NamedTextColor.BLACK));
        sign.getPersistentDataContainer().set(mailboxSignKey, PersistentDataType.BYTE, (byte) 1);
        sign.getPersistentDataContainer().set(mailboxOwnerKey, PersistentDataType.STRING, area.ownerId().toString());
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private void ensureMailboxSign(MailArea area) {
        if (area.signBlock() == null) return;
        String ownerName = nameOf(area.ownerId());
        placeMailboxSign(area, ownerName);
    }

    private void removeMailboxSign(MailArea area) {
        if (area.signBlock() == null) return;
        Location location = area.signBlock().location();
        if (location == null) return;
        Block block = location.getBlock();
        if (isMailboxSign(block)) block.setType(Material.AIR, false);
    }

    private List<String> splitMailboxName(String name) {
        String clean = name == null ? "Mailbox" : name.trim();
        if (clean.length() <= 16) return List.of(clean, "");
        int split = Math.min(16, clean.length());
        int space = clean.lastIndexOf(' ', split);
        if (space >= 4) split = space;
        String first = clean.substring(0, split).trim();
        String second = clean.substring(split).trim();
        return List.of(first, second);
    }

    private String readableStatus(Shipment shipment) {
        return switch (shipment.status()) {
            case IN_TRANSIT -> "in transit";
            case WAITING_FOR_SPACE -> "waiting for delivery space";
            case RETURNING -> "returning to sender";
            case WAITING_FOR_RETURN_SPACE -> "waiting for return space";
            case DELIVERED -> "delivered";
        };
    }

    private String nameOf(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() == null ? playerId.toString() : player.getName();
    }
}
