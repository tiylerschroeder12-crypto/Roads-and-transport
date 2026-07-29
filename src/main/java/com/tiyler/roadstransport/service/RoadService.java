package com.tiyler.roadstransport.service;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class RoadService implements Runnable {
    private final JavaPlugin plugin;
    private final int pathAmplifier;
    private final int poweredAmplifier;
    private final boolean affectMounted;

    public RoadService(JavaPlugin plugin) {
        this.plugin = plugin;
        pathAmplifier = plugin.getConfig().getInt("roads.path-speed-amplifier", 0);
        poweredAmplifier = plugin.getConfig().getInt("roads.powered-road-speed-amplifier", 1);
        affectMounted = plugin.getConfig().getBoolean("roads.affect-mounted-players", false);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnGround()) continue;
            if (!affectMounted && player.isInsideVehicle()) continue;
            if (player.isGliding() || player.isSwimming() || player.isFlying()) continue;
            Block feet = player.getLocation().getBlock();
            Block surface = feet.getType().isAir() || feet.isPassable() ? feet.getRelative(0, -1, 0) : feet;
            int amplifier = -1;
            if (surface.getType() == Material.DIRT_PATH) amplifier = pathAmplifier;
            Block underSurface = surface.getRelative(0, -1, 0);
            if (underSurface.getType() == Material.REDSTONE_BLOCK) amplifier = Math.max(amplifier, poweredAmplifier);
            if (amplifier < 0) continue;
            PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
            if (current != null && current.getAmplifier() > amplifier) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12, amplifier, true, false, false));
        }
    }
}
