package com.tiyler.roadstransport.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location location() {
        World world = Bukkit.getWorld(worldId);
        return world == null ? null : new Location(world, x, y, z);
    }

    public String serialize() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    public static BlockKey deserialize(String value) {
        String[] parts = value.split(":");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid block key: " + value);
        return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
