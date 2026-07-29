package com.tiyler.roadstransport.model;

import java.util.UUID;

public record HomeRecord(UUID ownerId, String name, BlockKey location, UUID generatedClaimId) {
}
