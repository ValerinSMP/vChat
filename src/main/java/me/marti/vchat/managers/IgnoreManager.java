package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IgnoreManager {

    private final VChat plugin;
    private final NamespacedKey ignoreKey;
    // Map<Ignorer, Set<Ignored>>
    private final Map<UUID, Set<UUID>> ignoreCache = new ConcurrentHashMap<>();

    public IgnoreManager(VChat plugin) {
        this.plugin = plugin;
        this.ignoreKey = new NamespacedKey(plugin, "ignored_players");
    }

    public void loadData(Player player) {
        Set<UUID> ignored = ConcurrentHashMap.newKeySet();
        if (player.getPersistentDataContainer().has(ignoreKey, PersistentDataType.STRING)) {
            String data = player.getPersistentDataContainer().get(ignoreKey, PersistentDataType.STRING);
            if (data != null && !data.isEmpty()) {
                String[] split = data.split(",");
                for (String s : split) {
                    try {
                        ignored.add(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        // ignore malformed
                    }
                }
            }
        }
        ignoreCache.put(player.getUniqueId(), ignored);
    }

    public void unloadData(Player player) {
        ignoreCache.remove(player.getUniqueId());
    }

    public boolean isIgnored(UUID ignorer, UUID target) {
        Set<UUID> ignored = ignoreCache.get(ignorer);
        return ignored != null && ignored.contains(target);
    }

    public void addIgnore(Player ignorer, UUID targetId) {
        Set<UUID> ignores = ignoreCache.computeIfAbsent(ignorer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        ignores.add(targetId);
        saveData(ignorer);
    }

    public void removeIgnore(Player ignorer, UUID targetId) {
        Set<UUID> ignores = ignoreCache.computeIfAbsent(ignorer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        ignores.remove(targetId);
        saveData(ignorer);
    }
    
    public Set<UUID> getIgnoredPlayers(Player player) {
        return ignoreCache.getOrDefault(player.getUniqueId(), Set.of());
    }

    private void saveData(Player player) {
        Set<UUID> ignores = ignoreCache.get(player.getUniqueId());
        if (ignores == null || ignores.isEmpty()) {
            player.getPersistentDataContainer().remove(ignoreKey);
        } else {
            String data = ignores.stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            player.getPersistentDataContainer().set(ignoreKey, PersistentDataType.STRING, data);
        }
    }
}
