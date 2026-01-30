package me.marti.vchat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;

public class MessageSanitizer {

    private static final MiniMessage miniMessage = MiniMessage.builder().build();

    public static String sanitize(Player player, String message) {
        if (player.hasPermission("vchat.format.bypass")) {
            return message;
        }

        TagResolver.Builder tagResolverBuilder = TagResolver.builder();

        if (player.hasPermission("vchat.format.color.basic")) {
            tagResolverBuilder.resolver(StandardTags.color());
        }

        if (player.hasPermission("vchat.format.style.basic")) {
            tagResolverBuilder.resolver(StandardTags.decorations());
        }



        if (player.hasPermission("vchat.format.color.hex")) {
            tagResolverBuilder.resolver(StandardTags.color());
            tagResolverBuilder.resolver(StandardTags.gradient());
            tagResolverBuilder.resolver(StandardTags.rainbow());
        }

        Component parsed = miniMessage.deserialize(message, tagResolverBuilder.build());
        return miniMessage.serialize(parsed);
    }
}