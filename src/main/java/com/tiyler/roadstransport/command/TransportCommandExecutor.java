package com.tiyler.roadstransport.command;

import com.tiyler.roadstransport.RoadsAndTransportPlugin;
import com.tiyler.roadstransport.bridge.KingdomsBridge;
import com.tiyler.roadstransport.service.*;
import com.tiyler.roadstransport.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransportCommandExecutor implements CommandExecutor, TabCompleter {
    private final RoadsAndTransportPlugin plugin;
    private final KingdomsBridge kingdoms;
    private final WaypointService waypoints;
    private final MailService mail;
    private final HomeService homes;
    private final HorseService horses;

    public TransportCommandExecutor(RoadsAndTransportPlugin plugin, KingdomsBridge kingdoms,
                                    WaypointService waypoints, MailService mail,
                                    HomeService homes, HorseService horses) {
        this.plugin = plugin;
        this.kingdoms = kingdoms;
        this.waypoints = waypoints;
        this.mail = mail;
        this.homes = homes;
        this.horses = horses;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) && !command.getName().equalsIgnoreCase("rat")) {
            Messages.error(sender, "This command must be used by a player.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "createwaypoint" -> createWaypoint((Player) sender, args);
            case "waypoint" -> waypoint((Player) sender, args);
            case "delwaypoint" -> waypoints.removeLooking((Player) sender);
            case "mailbox" -> mailbox((Player) sender, args);
            case "delmailbox" -> mail.removeArea((Player) sender);
            case "createmailboxfor" -> createMailboxFor((Player) sender, args);
            case "mail" -> mail((Player) sender, args);
            case "createhome" -> {
                if (args.length == 0) Messages.error(sender, "Usage: /createhome <home name>");
                else homes.create((Player) sender, join(args, 0));
            }
            case "home" -> {
                if (args.length == 0) {
                    homes.list((Player) sender);
                } else if (waypoints.hasSession(((Player) sender).getUniqueId())) {
                    Messages.error(sender, "You are already preparing to travel by waypoint.");
                } else {
                    homes.travel((Player) sender, join(args, 0));
                }
            }
            case "delhome" -> {
                if (args.length == 0) homes.listForDeletion((Player) sender);
                else homes.delete((Player) sender, join(args, 0));
            }
            case "horseinfo" -> horses.inspect((Player) sender);
            case "horsetrust" -> horseTrust((Player) sender, args, true);
            case "horseuntrust" -> horseTrust((Player) sender, args, false);
            case "rat" -> rat(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void createWaypoint(Player player, String[] args) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /createwaypoint <public <name>|personal>");
            return;
        }
        if (args[0].equalsIgnoreCase("personal")) {
            waypoints.createPersonal(player);
            return;
        }
        if (args[0].equalsIgnoreCase("public") && args.length >= 2) {
            waypoints.createPublic(player, join(args, 1));
            return;
        }
        Messages.error(player, "Usage: /createwaypoint <public <name>|personal>");
    }

    private void waypoint(Player player, String[] args) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /waypoint <adopt|info|access add/remove <player>>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "adopt" -> waypoints.adoptLooking(player);
            case "info" -> waypoints.infoLooking(player);
            case "access" -> {
                if (args.length < 3 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                    Messages.error(player, "Usage: /waypoint access <add|remove> <player>");
                    return;
                }
                OfflinePlayer target = kingdoms.offlinePlayer(args[2]);
                if (target == null) {
                    Messages.error(player, "That player could not be found.");
                    return;
                }
                waypoints.access(player, args[1].equalsIgnoreCase("add"), target);
            }
            default -> Messages.error(player, "Usage: /waypoint <adopt|info|access add/remove <player>>");
        }
    }

    private void createMailboxFor(Player player, String[] args) {
        if (args.length < 2) {
            Messages.error(player, "Usage: /createmailboxfor <player> <mailbox name>");
            return;
        }
        OfflinePlayer target = kingdoms.offlinePlayer(args[0]);
        if (target == null) {
            Messages.error(player, "That player could not be found.");
            return;
        }
        mail.createAreaFor(player, target, join(args, 1));
    }

    private void mailbox(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            mail.areaInfo(player);
            return;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                Messages.error(player, "Usage: /mailbox create <mailbox name>");
                return;
            }
            mail.createArea(player, join(args, 1));
            return;
        }
        Messages.error(player, "Usage: /mailbox <create <mailbox name>|info>");
    }

    private void mail(Player player, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            mail.status(player);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("send")) {
            OfflinePlayer target = kingdoms.offlinePlayer(args[1]);
            mail.send(player, target, false);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("rush") && args[1].equalsIgnoreCase("send")) {
            OfflinePlayer target = kingdoms.offlinePlayer(args[2]);
            mail.send(player, target, true);
            return;
        }
        Messages.error(player, "Usage: /mail <send <player>|rush send <player>|status>");
    }

    private void horseTrust(Player player, String[] args, boolean add) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /" + (add ? "horsetrust" : "horseuntrust") + " <player>");
            return;
        }
        OfflinePlayer target = kingdoms.offlinePlayer(args[0]);
        if (target == null) {
            Messages.error(player, "That player could not be found.");
            return;
        }
        horses.trust(player, target, add);
    }

    private void rat(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                Messages.info(sender, "RoadsAndTransport commands:");
                Messages.info(sender, "/createwaypoint public <name>, /createwaypoint personal, /waypoint <adopt|info|access>, /delwaypoint");
                Messages.info(sender, "/mailbox create <name>, /mailbox info, /delmailbox, /createmailboxfor <player> <name>");
                Messages.info(sender, "/mail <send|rush send|status>");
                Messages.info(sender, "/createhome <name>, /home [name], /delhome [name]");
                Messages.info(sender, "/horseinfo, /horsetrust <player>, /horseuntrust <player>");
            }
            case "save" -> {
                if (!sender.hasPermission("roadsandtransport.admin")) {
                    Messages.error(sender, "You do not have permission.");
                    return;
                }
                plugin.saveAllData();
                Messages.success(sender, "RoadsAndTransport data saved.");
            }
            case "reload" -> {
                if (!sender.hasPermission("roadsandtransport.admin")) {
                    Messages.error(sender, "You do not have permission.");
                    return;
                }
                plugin.reloadConfig();
                Messages.success(sender, "Configuration reloaded. Restart the server to apply service interval and cached-value changes.");
            }
            default -> Messages.error(sender, "Usage: /rat <help|save|reload>");
        }
    }

    private String join(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)).trim();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("createwaypoint") && args.length == 1) values.addAll(List.of("public", "personal"));
        if (name.equals("waypoint")) {
            if (args.length == 1) values.addAll(List.of("adopt", "info", "access"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("access")) values.addAll(List.of("add", "remove"));
        }
        if (name.equals("createmailboxfor") && args.length == 1) {
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (name.equals("mailbox") && args.length == 1) values.addAll(List.of("create", "info"));
        if (name.equals("mail")) {
            if (args.length == 1) values.addAll(List.of("send", "rush", "status"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("rush")) values.add("send");
        }
        if ((name.equals("home") || name.equals("delhome")) && args.length == 1 && sender instanceof Player player) {
            values.addAll(homes.names(player.getUniqueId()));
        }
        if (name.equals("rat") && args.length == 1) values.addAll(List.of("help", "save", "reload"));
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
