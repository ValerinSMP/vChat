package me.marti.vchat.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeRouteValidatorTest {

    @Test
    void rejectsDefaultPlaceholderWebhookUrls() {
        assertFalse(BridgeRouteValidator.isUsableWebhookUrl("YOUR_CUSTOM_WEBHOOK_URL"));
        assertFalse(BridgeRouteValidator.isUsableWebhookUrl("YOUR_PVP_WEBHOOK_URL"));
    }

    @Test
    void acceptsHttpWebhookUrlsOnly() {
        assertTrue(BridgeRouteValidator.isUsableWebhookUrl("https://discord.com/api/webhooks/123/token"));
        assertTrue(BridgeRouteValidator.isUsableWebhookUrl("http://localhost/webhook"));
        assertFalse(BridgeRouteValidator.isUsableWebhookUrl(""));
        assertFalse(BridgeRouteValidator.isUsableWebhookUrl("not-a-url"));
    }

    @Test
    void detectsBridgeRouteAsUsableOnlyWhenChannelAndWebhookAreConfigured() {
        assertTrue(BridgeRouteValidator.isUsableRoute("1234567890", "https://discord.com/api/webhooks/123/token"));
        assertFalse(BridgeRouteValidator.isUsableRoute("YOUR_CUSTOM_CHANNEL_ID", "https://discord.com/api/webhooks/123/token"));
        assertFalse(BridgeRouteValidator.isUsableRoute("1234567890", "YOUR_CUSTOM_WEBHOOK_URL"));
    }
}
