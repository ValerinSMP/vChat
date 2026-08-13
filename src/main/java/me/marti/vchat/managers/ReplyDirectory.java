package me.marti.vchat.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ReplyDirectory {
    private final Map<UUID, Contact> contacts = new ConcurrentHashMap<>();

    void link(UUID firstId, String firstName, UUID secondId, String secondName) {
        contacts.put(firstId, new Contact(secondId, secondName));
        contacts.put(secondId, new Contact(firstId, firstName));
    }

    Contact get(UUID playerId) { return contacts.get(playerId); }

    record Contact(UUID playerId, String playerName) { }
}
