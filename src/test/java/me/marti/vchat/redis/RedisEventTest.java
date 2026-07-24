package me.marti.vchat.redis;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedisEventTest {

    private final Gson gson = new Gson();

    @Test
    void roundTripsAllFieldsThroughJson() {
        RedisEvent original = new RedisEvent();
        original.type = RedisEventType.PRIVATE_MSG;
        original.sourceServer = "survival1";
        original.senderName = "Marti";
        original.senderUuid = "11111111-1111-1111-1111-111111111111";
        original.targetName = "Valerin";
        original.targetUuid = "22222222-2222-2222-2222-222222222222";
        original.componentJson = "{\"text\":\"hola\"}";
        original.timestamp = 123456789L;

        String json = gson.toJson(original);
        RedisEvent parsed = gson.fromJson(json, RedisEvent.class);

        assertEquals(original.type, parsed.type);
        assertEquals(original.sourceServer, parsed.sourceServer);
        assertEquals(original.senderName, parsed.senderName);
        assertEquals(original.senderUuid, parsed.senderUuid);
        assertEquals(original.targetName, parsed.targetName);
        assertEquals(original.targetUuid, parsed.targetUuid);
        assertEquals(original.componentJson, parsed.componentJson);
        assertEquals(original.timestamp, parsed.timestamp);
    }

    @Test
    void unknownEnumValueLeavesTypeNullInsteadOfThrowing() {
        // Gson NO lanza al toparse con un valor de enum desconocido — deja el campo en null.
        // RedisManager.isLoopback() depende de este comportamiento exacto para descartar el
        // evento (ver RedisManagerTest#eventsWithNullTypeAreTreatedAsLoopbackAndDropped); si
        // una futura versión de Gson empieza a lanzar acá, ese test lo va a evidenciar.
        RedisEvent parsed = gson.fromJson("{\"type\":\"NOT_A_REAL_TYPE\"}", RedisEvent.class);
        assertNull(parsed.type);
    }
}
