package me.marti.vchat.processors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemChatFormatterTest {

    @Test
    void renderItemFormatUsesSnapshotAmount() {
        String plain = PlainTextComponentSerializer.plainText().serialize(
                ItemChatFormatter.render("<gray>{amount}x {item}</gray>", 2, Component.text("Diamond Sword")));

        assertEquals("2x Diamond Sword", plain);
    }

    @Test
    void visibleNameFallsBackToPrettifiedMaterialName() {
        assertEquals("Netherite Chestplate", ItemChatFormatter.prettifyMaterialName("NETHERITE_CHESTPLATE"));
    }
}
