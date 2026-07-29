package com.tiyler.roadstransport.model;

import org.bukkit.Location;

import java.util.UUID;

public record TravelSession(UUID playerId, UUID destinationId, Location origin, long reservedFare,
                            long startedAtMillis, int taskId, String kind) {
}
