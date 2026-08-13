package me.marti.vchat.redis;

public enum DeliveryStatus {
    DELIVERED,
    OFFLINE,
    BLOCKED,
    INVALID;

    public boolean delivered() { return this == DELIVERED; }

    public static DeliveryStatus parse(String value) {
        try { return valueOf(value); }
        catch (RuntimeException invalid) { return INVALID; }
    }
}
