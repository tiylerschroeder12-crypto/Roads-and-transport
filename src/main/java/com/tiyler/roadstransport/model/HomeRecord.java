package com.tiyler.roadstransport.model;

import java.util.UUID;

public record HomeRecord(UUID ownerId, String name, BlockKey location, UUID legacyGeneratedClaimId, long visitCount) {
    public HomeRecord withoutLegacyClaim() {
        return new HomeRecord(ownerId, name, location, null, visitCount);
    }

    public HomeRecord withVisitCount(long newVisitCount) {
        return new HomeRecord(ownerId, name, location, legacyGeneratedClaimId, Math.max(0L, newVisitCount));
    }
}
