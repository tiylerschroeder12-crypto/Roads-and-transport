package com.tiyler.roadstransport.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WaypointRecord {
    private final UUID id;
    private final WaypointType type;
    private String name;
    private final BlockKey block;
    private final UUID creatorId;
    private UUID ownerId;
    private UUID claimId;
    private String claimName;
    private long storedGold;
    private final Set<UUID> authorized = new HashSet<>();

    public WaypointRecord(UUID id, WaypointType type, String name, BlockKey block, UUID creatorId,
                          UUID ownerId, UUID claimId, String claimName) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.block = block;
        this.creatorId = creatorId;
        this.ownerId = ownerId;
        this.claimId = claimId;
        this.claimName = claimName;
    }

    public UUID id() { return id; }
    public WaypointType type() { return type; }
    public String name() { return name; }
    public BlockKey block() { return block; }
    public UUID creatorId() { return creatorId; }
    public UUID ownerId() { return ownerId; }
    public UUID claimId() { return claimId; }
    public String claimName() { return claimName; }
    public long storedGold() { return storedGold; }
    public Set<UUID> authorized() { return authorized; }

    public void name(String name) { this.name = name; }
    public void ownerId(UUID ownerId) { this.ownerId = ownerId; }
    public void claimId(UUID claimId) { this.claimId = claimId; }
    public void claimName(String claimName) { this.claimName = claimName; }
    public void storedGold(long storedGold) { this.storedGold = Math.max(0, storedGold); }

    public boolean canUse(UUID playerId) {
        return type == WaypointType.PUBLIC || ownerId.equals(playerId) || authorized.contains(playerId);
    }
}
