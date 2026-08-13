package me.marti.vchat.managers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplyDirectoryTest {
    @Test
    void replyKeepsExactUuidWhenContactChangesServer() {
        ReplyDirectory replies = new ReplyDirectory();
        UUID sender = UUID.randomUUID();
        UUID remote = UUID.randomUUID();
        replies.link(sender, "Sender", remote, "Remote");
        assertEquals(remote, replies.get(sender).playerId());
        assertEquals("Remote", replies.get(sender).playerName());
    }
}
