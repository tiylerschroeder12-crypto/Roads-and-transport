package com.tiyler.roadstransport.model;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class HorseRecord {
    private final UUID horseId;
    private UUID ownerId;
    private int speedTier;
    private boolean enchantedAppleUpgradeApplied;
    private double baseMovementSpeed;
    private boolean cargoAttached;
    private final List<ItemStack> cargo = new ArrayList<>(Collections.nCopies(54, null));
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<ChunkKey> visitedChunks = new HashSet<>();
    private int partialChunkProgress;
    private long pendingRewardGold;

    public HorseRecord(UUID horseId) {
        this.horseId = horseId;
    }

    public UUID horseId() { return horseId; }
    public UUID ownerId() { return ownerId; }
    public int speedTier() { return speedTier; }
    public boolean enchantedAppleUpgradeApplied() { return enchantedAppleUpgradeApplied; }
    public double baseMovementSpeed() { return baseMovementSpeed; }
    public boolean cargoAttached() { return cargoAttached; }
    public List<ItemStack> cargo() { return cargo; }
    public Set<UUID> trusted() { return trusted; }
    public Set<ChunkKey> visitedChunks() { return visitedChunks; }
    public int partialChunkProgress() { return partialChunkProgress; }
    public long pendingRewardGold() { return pendingRewardGold; }

    public void ownerId(UUID ownerId) { this.ownerId = ownerId; }
    public void speedTier(int speedTier) { this.speedTier = Math.max(0, speedTier); }
    public void enchantedAppleUpgradeApplied(boolean value) { enchantedAppleUpgradeApplied = value; }
    public void baseMovementSpeed(double baseMovementSpeed) { this.baseMovementSpeed = baseMovementSpeed; }
    public void cargoAttached(boolean cargoAttached) { this.cargoAttached = cargoAttached; }
    public void partialChunkProgress(int value) { partialChunkProgress = Math.max(0, value); }
    public void pendingRewardGold(long value) { pendingRewardGold = Math.max(0, value); }

    public boolean mayAccess(UUID playerId) {
        return playerId != null && (playerId.equals(ownerId) || trusted.contains(playerId));
    }

    public void clearJourney() {
        visitedChunks.clear();
        partialChunkProgress = 0;
    }
}
