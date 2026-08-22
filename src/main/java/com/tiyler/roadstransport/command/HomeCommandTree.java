package com.tiyler.roadstransport.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.tiyler.roadstransport.service.HomeService;
import com.tiyler.roadstransport.service.WaypointService;
import com.tiyler.roadstransport.util.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Real Brigadier command trees for /home and /delhome.
 *
 * The home-name node is a greedy-string argument with a live suggestion provider.
 * This is deliberate: when a player types `/home ` or `/delhome `, the client has
 * an argument node to query immediately and Paper returns every saved home name.
 * Multi-word home names are suggested as one complete greedy-string value.
 */
public final class HomeCommandTree {
    private HomeCommandTree() {
    }

    public static LiteralCommandNode<CommandSourceStack> home(HomeService homes, WaypointService waypoints) {
        return Commands.literal("home")
                .requires(source -> source.getSender().hasPermission("roadsandtransport.use"))
                .executes(ctx -> {
                    Player player = player(ctx.getSource());
                    if (player == null) return Command.SINGLE_SUCCESS;
                    Messages.info(player, "Usage: /home <name> — use /homelist to see your saved homes.");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("home", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            Player player = playerSilently(ctx.getSource());
                            if (player == null) return builder.buildFuture();

                            String remaining = builder.getRemainingLowerCase();
                            homes.names(player.getUniqueId()).stream()
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Player player = player(ctx.getSource());
                            if (player == null) return Command.SINGLE_SUCCESS;

                            String homeName = StringArgumentType.getString(ctx, "home").trim();
                            if (waypoints.hasSession(player.getUniqueId())) {
                                Messages.error(player, "You are already preparing to travel by waypoint.");
                                return Command.SINGLE_SUCCESS;
                            }
                            homes.travel(player, homeName);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> deleteHome(HomeService homes) {
        return Commands.literal("delhome")
                .requires(source -> source.getSender().hasPermission("roadsandtransport.use"))
                .executes(ctx -> {
                    Player player = player(ctx.getSource());
                    if (player == null) return Command.SINGLE_SUCCESS;
                    homes.listForDeletion(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("home", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            Player player = playerSilently(ctx.getSource());
                            if (player == null) return builder.buildFuture();

                            String remaining = builder.getRemainingLowerCase();
                            homes.names(player.getUniqueId()).stream()
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Player player = player(ctx.getSource());
                            if (player == null) return Command.SINGLE_SUCCESS;
                            homes.delete(player, StringArgumentType.getString(ctx, "home").trim());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static Player player(CommandSourceStack source) {
        Player player = playerSilently(source);
        if (player == null) Messages.error(source.getSender(), "This command must be used by a player.");
        return player;
    }

    private static Player playerSilently(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) return player;
        if (source.getSender() instanceof Player player) return player;
        return null;
    }
}
