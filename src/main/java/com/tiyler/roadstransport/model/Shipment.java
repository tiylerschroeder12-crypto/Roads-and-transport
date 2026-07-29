package com.tiyler.roadstransport.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Shipment {
    private final UUID id;
    private final UUID senderId;
    private final UUID recipientId;
    private final String senderName;
    private final String senderClaimName;
    private final long sentAtMillis;
    private long dueAtMillis;
    private final boolean rush;
    private ShipmentStatus status;
    private final String barrelBlockData;
    private final List<ItemStack> contents;
    private BlockKey deliveredBlock;
    private UUID deliveredToId;

    public Shipment(UUID id, UUID senderId, UUID recipientId, String senderName, String senderClaimName,
                    long sentAtMillis, long dueAtMillis, boolean rush, ShipmentStatus status,
                    String barrelBlockData, List<ItemStack> contents) {
        this.id = id;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.senderClaimName = senderClaimName;
        this.sentAtMillis = sentAtMillis;
        this.dueAtMillis = dueAtMillis;
        this.rush = rush;
        this.status = status;
        this.barrelBlockData = barrelBlockData;
        this.contents = new ArrayList<>();
        for (ItemStack item : contents) this.contents.add(item == null ? null : item.clone());
    }

    public UUID id() { return id; }
    public UUID senderId() { return senderId; }
    public UUID recipientId() { return recipientId; }
    public String senderName() { return senderName; }
    public String senderClaimName() { return senderClaimName; }
    public long sentAtMillis() { return sentAtMillis; }
    public long dueAtMillis() { return dueAtMillis; }
    public boolean rush() { return rush; }
    public ShipmentStatus status() { return status; }
    public String barrelBlockData() { return barrelBlockData; }
    public List<ItemStack> contents() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : contents) copy.add(item == null ? null : item.clone());
        return copy;
    }
    public BlockKey deliveredBlock() { return deliveredBlock; }
    public UUID deliveredToId() { return deliveredToId; }

    public void dueAtMillis(long value) { dueAtMillis = value; }
    public void status(ShipmentStatus value) { status = value; }
    public void deliveredBlock(BlockKey value) { deliveredBlock = value; }
    public void deliveredToId(UUID value) { deliveredToId = value; }
}
