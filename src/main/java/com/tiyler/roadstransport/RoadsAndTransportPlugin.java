package com.tiyler.roadstransport;

import com.tiyler.roadstransport.bridge.KingdomsBridge;
import com.tiyler.roadstransport.command.TransportCommandExecutor;
import com.tiyler.roadstransport.data.DataManager;
import com.tiyler.roadstransport.listener.*;
import com.tiyler.roadstransport.service.*;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class RoadsAndTransportPlugin extends JavaPlugin {
    private DataManager dataManager;
    private KingdomsBridge kingdoms;
    private WaypointService waypoints;
    private MailService mail;
    private HomeService homes;
    private HorseService horses;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        try {
            kingdoms = new KingdomsBridge(this);
        } catch (Exception ex) {
            getLogger().severe("Could not connect to KingdomsAndCurrency: " + ex.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        dataManager = new DataManager(this);
        waypoints = new WaypointService(this, dataManager, kingdoms);
        mail = new MailService(this, dataManager, kingdoms);
        homes = new HomeService(this, dataManager, kingdoms);
        horses = new HorseService(this, dataManager);
        RoadService roads = new RoadService(this);

        TransportCommandExecutor commands = new TransportCommandExecutor(this, kingdoms, waypoints, mail, homes, horses);
        for (String commandName : List.of(
                "createwaypoint", "waypoint", "delwaypoint", "mailbox", "delmailbox", "createmailboxfor", "mail",
                "createhome", "home", "delhome", "horseinfo", "horsetrust", "horseuntrust", "rat"
        )) {
            PluginCommand command = getCommand(commandName);
            if (command == null) {
                getLogger().severe("Command missing from plugin.yml: " + commandName);
                continue;
            }
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }

        var manager = Bukkit.getPluginManager();
        manager.registerEvents(new WaypointTravelListener(waypoints, homes), this);
        manager.registerEvents(new MailListener(mail, waypoints), this);
        manager.registerEvents(new HorseListener(horses), this);

        long roadInterval = Math.max(1L, getConfig().getLong("roads.check-interval-ticks", 5L));
        Bukkit.getScheduler().runTaskTimer(this, roads, 1L, roadInterval);
        Bukkit.getScheduler().runTask(this, () -> {
            waypoints.ensureAllDisplays();
            mail.removeAllLabels();
            mail.ensureLabels();
            mail.ensureAreaSigns();
        });
        Bukkit.getScheduler().runTaskTimer(this, waypoints::tickDisplays, 20L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, mail::ensureLabels, 20L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, mail::ensureAreaSigns, 20L, 200L);
        long mailRetry = Math.max(20L, getConfig().getLong("mail.retry-interval-ticks", 200L));
        Bukkit.getScheduler().runTaskTimer(this, mail::processDueShipments, mailRetry, mailRetry);
        long horseMovement = Math.max(1L, getConfig().getLong("horses.movement-scan-interval-ticks", 10L));
        Bukkit.getScheduler().runTaskTimer(this, horses::scanMovement, horseMovement, horseMovement);
        long hostileInterval = Math.max(20L, getConfig().getLong("horses.hostile-target-interval-ticks", 40L));
        Bukkit.getScheduler().runTaskTimer(this, horses::targetCargoHorsesAtNight, hostileInterval, hostileInterval);
        long autosave = Math.max(200L, getConfig().getLong("data.autosave-ticks", 1200L));
        Bukkit.getScheduler().runTaskTimer(this, this::saveAllData, autosave, autosave);

        getLogger().info("RoadsAndTransport " + getPluginMeta().getVersion() + " enabled with "
                + waypoints.all().size() + " waypoints.");
    }

    private void migrateConfig() {
        boolean changed = false;
        if (getConfig().getBoolean("homes.blocked-at-night", true)) {
            getConfig().set("homes.blocked-at-night", false);
            changed = true;
        }
        // 0.1.5 shipped with a two-home default. Homes are pure teleport anchors now,
        // so migrate that old default to the new four-home limit. Other custom values remain intact.
        if (getConfig().getInt("homes.maximum", 2) == 2) {
            getConfig().set("homes.maximum", 4);
            changed = true;
        }
        if (!getConfig().isSet("horses.maximum-linked-horses")) {
            getConfig().set("horses.maximum-linked-horses", 4);
            changed = true;
        }
        if (!getConfig().isSet("horses.speed-penalty-per-additional-horse")) {
            getConfig().set("horses.speed-penalty-per-additional-horse", 0.10);
            changed = true;
        }
        if (!getConfig().isSet("horses.minimum-caravan-speed-multiplier")) {
            getConfig().set("horses.minimum-caravan-speed-multiplier", 0.55);
            changed = true;
        }
        if (changed) saveConfig();
    }

    @Override
    public void onDisable() {
        if (waypoints != null) waypoints.shutdown();
        if (homes != null) homes.shutdown();
        if (horses != null) horses.shutdown();
        saveAllData();
    }

    public void saveAllData() {
        if (waypoints != null) waypoints.save();
        if (mail != null) mail.save();
        if (homes != null) homes.save();
        if (horses != null) horses.save();
    }
}
