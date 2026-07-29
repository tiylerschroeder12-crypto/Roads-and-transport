package com.tiyler.roadstransport.model;

import java.util.UUID;

public record MailArea(UUID ownerId, BlockKey center, String name, BlockKey signBlock) {
}
