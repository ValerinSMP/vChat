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
        // PDC local no viaja entre servers al saltar de red — si el jugador puso /ignore
        // en otro server del cluster, esa lista solo existe en Redis hasta ahora, nunca
        // se leía de vuelta acá. Sin esto, cada salto de server "olvidaba" a quién tenía ignorado.
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
        Set<UUID> ignores = ignoreCache.getOrDefault(player.getUniqueId(), Set.of());
        if (ignores.isEmpty()) {
            player.getPersistentDataContainer().remove(ignoreKey);
        } else {
            String data = ignores.stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            player.getPersistentDataContainer().set(ignoreKey, PersistentDataType.STRING, data);
        }
        plugin.savePlayerState(player);
    }

    public void applyState(Player player, Set<UUID> ignores) {
        Set<UUID> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(ignores);
        ignoreCache.put(player.getUniqueId(), copy);
        if (copy.isEmpty()) {
            player.getPersistentDataContainer().remove(ignoreKey);
        } else {
            player.getPersistentDataContainer().set(ignoreKey, PersistentDataType.STRING,
                    copy.stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
    }
}
