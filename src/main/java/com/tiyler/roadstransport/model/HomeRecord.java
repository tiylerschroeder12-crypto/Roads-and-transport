package com.tiyler.roadstransport.model;

import java.util.UUID;

public record HomeRecord(UUID ownerId, String name, BlockKey location, UUID legacyGeneratedClaimId) {
    public HomeRecord withoutLegacyClaim() {
        return new HomeRecord(ownerId, name, location, null);
    }
}
