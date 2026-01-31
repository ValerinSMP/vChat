package me.marti.vchat.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import me.marti.vchat.VChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class MentionsTabInjector implements Listener {

    private final VChat plugin;
    private boolean disabled = false;

    public MentionsTabInjector(VChat plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("DEBUG: MentionsTabInjector Initialized!");
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Inject for current online players (Reload support)
        for (Player p : Bukkit.getOnlinePlayers()) {
            injectFor(p);
        }
    }

    public void shutdown() {
        // Remove all injections on plugin disable
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeFor(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        injectFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFor(event.getPlayer());
    }

    private void injectFor(Player joiner) {
        // Strategy: Simple Injection
        // Only inject @Name. User must type "@" or see it in the list.

        List<PlayerInfoData> listForJoiner = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(joiner.getUniqueId()))
                continue;

            // Only entry: @Name
            PlayerInfoData fakeData = createFakeData(p, "@" + p.getName(), "@" + p.getName());
            if (fakeData != null) listForJoiner.add(fakeData);
        }

        if (!listForJoiner.isEmpty()) {
            sendPacket(joiner, listForJoiner);
        }

        // Joiner to others
        List<PlayerInfoData> joinerFakes = new ArrayList<>();
        PlayerInfoData joinerFakeData = createFakeData(joiner, "@" + joiner.getName(), "@" + joiner.getName());
        if (joinerFakeData != null) {
            joinerFakes.add(joinerFakeData);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(joiner.getUniqueId()))
                    continue;
                sendPacket(p, joinerFakes);
            }
        }
    }

    private void removeFor(Player quitter) {
        List<UUID> uuids = new ArrayList<>();
        uuids.add(getFakeUUID("@" + quitter.getName()));

        try {
            PacketType removeType = PacketType.Play.Server.PLAYER_INFO_REMOVE;
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(removeType);
            packet.getUUIDLists().write(0, uuids);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(quitter.getUniqueId()))
                    continue;
                try {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable t) {
            plugin.getLogger()
                    .warning("Could not remove fake mention entry for " + quitter.getName() + " - " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void sendPacket(Player target, List<PlayerInfoData> data) {
        if (disabled)
            return;
            
        try {
            // Modern Path via Reflection with NMS fallback
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);

            // 1. Get the correct Action Enum Class (NMS)
            // Found via debug inspection: net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action
            Class<?> actionEnumClass;
            try {
                actionEnumClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            } catch (ClassNotFoundException e) {
                // If NMS is remapped or different, we can try ProtocolLib packet field inspection
                throw new ClassNotFoundException("Could not find NMS Action Enum. Environment mismatch?", e);
            }

            Object addPlayerEnum = Enum.valueOf((Class<Enum>) actionEnumClass, "ADD_PLAYER");

            // 2. Create EnumSet via reflection
            Set<Enum> actions = EnumSet.of((Enum) addPlayerEnum);
            
            // 3. Write actions to index 0
            packet.getModifier().write(0, actions);

            // 4. Write data list to index 1
            packet.getPlayerInfoDataLists().write(1, data);

            ProtocolLibrary.getProtocolManager().sendServerPacket(target, packet);
        } catch (Throwable t) {
            plugin.getLogger().warning("[vChat] Failed to inject mentions: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            plugin.getLogger().warning("[vChat] Disabling Tab-Complete features to prevent spam.");
            this.disabled = true;
        }
    }

    private PlayerInfoData createFakeData(Player realPlayer, String profileName, String displayName) {
        try {
            WrappedGameProfile realProfile = WrappedGameProfile.fromPlayer(realPlayer);
            
            // Fix: Truncate profile name to 16 chars to prevent EncoderException
            // GameProfile names are strictly limited to 16 chars in Protocol
            String safeProfileName = profileName;
            if (safeProfileName.length() > 16) {
                safeProfileName = safeProfileName.substring(0, 16);
            }

            WrappedGameProfile fakeProfile = new WrappedGameProfile(getFakeUUID(profileName), safeProfileName);

            // Copy Properties (Skin)
            fakeProfile.getProperties().putAll(realProfile.getProperties());
            
            WrappedChatComponent displayComponent = WrappedChatComponent.fromText(displayName);

            // Use Legacy Constructor (4 args)
            // ProtocolLib should handle the internal conversion to modern data if using Legacy Packet API
            return new PlayerInfoData(fakeProfile, 0, NativeGameMode.SURVIVAL, displayComponent);
        } catch (Throwable t) {
            plugin.getLogger().warning("[vChat] Error creating fake data: " + t.getMessage());
            return null;
        }
    }

    private UUID getFakeUUID(String name) {
        return UUID.nameUUIDFromBytes(("vChatMention:" + name).getBytes());
    }
}
