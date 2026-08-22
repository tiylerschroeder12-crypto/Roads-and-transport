package com.tiyler.roadstransport.command;

import com.tiyler.roadstransport.service.HomeService;
import com.tiyler.roadstransport.service.WaypointService;
import com.tiyler.roadstransport.util.Messages;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Paper/Brigadier command wrapper for /home and /delhome.
 *
 * Using BasicCommand here is intentional: Paper can advertise the saved-home
 * suggestion provider directly in the command tree instead of relying on the
 * legacy Bukkit TabCompleter bridge, which some modern clients/server command
 * trees never query for these commands.
 */
public final class HomePaperCommand implements BasicCommand {
    private final HomeService homes;
    private final WaypointService waypoints;
    private final boolean deletion;

    public HomePaperCommand(HomeService homes, WaypointService waypoints, boolean deletion) {
        this.homes = homes;
        this.waypoints = waypoints;
        this.deletion = deletion;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            Messages.error(sender, "This command must be used by a player.");
            return;
        }

        if (args.length == 0 || join(args, 0).isBlank()) {
            if (deletion) homes.listForDeletion(player);
            else homes.list(player);
            return;
        }

        String homeName = join(args, 0);
        if (deletion) {
            homes.delete(player, homeName);
            return;
        }

        if (waypoints.hasSession(player.getUniqueId())) {
            Messages.error(player, "You are already preparing to travel by waypoint.");
            return;
        }
        homes.travel(player, homeName);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) return List.of();
        List<String> homeNames = homes.names(player.getUniqueId());
        if (homeNames.isEmpty()) return List.of();

        // BasicCommand suggestions replace the argument currently being typed. Keep
        // multi-word home names usable by returning the remaining suffix once the
        // earlier words have already been entered.
        if (args.length == 0) return homeNames;

        int currentIndex = args.length - 1;
        String completedPrefix = currentIndex == 0
                ? ""
                : String.join(" ", Arrays.copyOfRange(args, 0, currentIndex)).trim();
        String currentToken = args[currentIndex];
        String typed = completedPrefix.isEmpty()
                ? currentToken
                : completedPrefix + " " + currentToken;
        String typedLower = typed.toLowerCase(Locale.ROOT);

        List<String> suggestions = new ArrayList<>();
        for (String homeName : homeNames) {
            if (!homeName.toLowerCase(Locale.ROOT).startsWith(typedLower)) continue;
            if (completedPrefix.isEmpty()) {
                suggestions.add(homeName);
            } else {
                int suffixStart = Math.min(homeName.length(), completedPrefix.length() + 1);
                suggestions.add(homeName.substring(suffixStart));
            }
        }
        return suggestions;
    }

    @Override
    public String permission() {
        return "roadsandtransport.use";
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }
}
