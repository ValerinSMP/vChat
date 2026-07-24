package me.marti.vchat.redis;

/** Sobre pub/sub. Serializado con Gson, un solo canal por cluster. */
public class RedisEvent {

    public RedisEventType type;
    public String sourceServer;
    public String senderName;
    public String senderUuid;
    public String targetName;
    public String targetUuid;
    /** Component ya renderizado (GsonComponentSerializer) — el receptor no re-formatea. */
    public String componentJson;
    public long timestamp;
}
