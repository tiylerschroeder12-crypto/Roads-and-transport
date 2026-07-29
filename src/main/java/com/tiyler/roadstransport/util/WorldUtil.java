package com.tiyler.roadstransport.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;

public final class WorldUtil {
    private static final Set<Material> HAZARDS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE
    );

    private WorldUtil() {}

    public static boolean isNight(World world, long nightStart, long nightEnd) {
        long time = world.getTime();
        return time >= nightStart && time <= nightEnd;
    }

    public static Location findSafePlayerArrival(Location center, int radius) {
        World world = center.getWorld();
        int baseY = center.getBlockY();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -2; dy <= 3; dy++) {
                        int x = center.getBlockX() + dx;
                        int y = baseY + dy;
                        int z = center.getBlockZ() + dz;
                        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                        Block feet = world.getBlockAt(x, y, z);
                        Block head = world.getBlockAt(x, y + 1, z);
                        Block floor = world.getBlockAt(x, y - 1, z);
                        if (!feet.isPassable() || !head.isPassable()) continue;
                        if (!floor.getType().isSolid() || HAZARDS.contains(floor.getType())) continue;
                        if (HAZARDS.contains(feet.getType()) || HAZARDS.contains(head.getType())) continue;
                        return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), center.getPitch());
                    }
                }
            }
        }
        return null;
    }

    public static Location findSafeBarrelPlacement(Location center, int radius) {
        World world = center.getWorld();
        int baseY = center.getBlockY();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        int x = center.getBlockX() + dx;
                        int y = baseY + dy;
                        int z = center.getBlockZ() + dz;
                        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                        Block place = world.getBlockAt(x, y, z);
                        Block above = world.getBlockAt(x, y + 1, z);
                        Block floor = world.getBlockAt(x, y - 1, z);
                        if (!place.getType().isAir() || !above.isPassable()) continue;
                        if (!floor.getType().isSolid() || HAZARDS.contains(floor.getType())) continue;
                        return place.getLocation();
                    }
                }
            }
        }
        return null;
    }
}
