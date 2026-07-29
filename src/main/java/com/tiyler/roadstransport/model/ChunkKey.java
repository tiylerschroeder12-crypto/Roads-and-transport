package com.tiyler.roadstransport.model;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey of(Location location) {
        return of(location.getChunk());
    }

    public boolean adjacentOrSame(ChunkKey other, int maximumStep) {
        return other != null && worldId.equals(other.worldId)
                && Math.abs(x - other.x) <= maximumStep
                && Math.abs(z - other.z) <= maximumStep;
    }

    public String serialize() {
        return worldId + ":" + x + ":" + z;
    }

    public static ChunkKey deserialize(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid chunk key: " + value);
        return new ChunkKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
