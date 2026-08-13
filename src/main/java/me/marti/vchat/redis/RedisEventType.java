package me.marti.vchat.redis;

public enum RedisEventType {
    CHAT,
    PRIVATE_MSG,
    PRIVATE_ACK,
    SOCIAL_SPY,
    JOIN,
    QUIT,
    GLOBAL_MUTE_INVALIDATE,
    PREFERENCE_INVALIDATE
}
