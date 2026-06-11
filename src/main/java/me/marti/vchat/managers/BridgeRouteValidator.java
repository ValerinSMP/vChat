package me.marti.vchat.managers;

final class BridgeRouteValidator {

    private BridgeRouteValidator() {
    }

    static boolean isUsableRoute(String channelId, String webhookUrl) {
        return isConfiguredValue(channelId) && isUsableWebhookUrl(webhookUrl);
    }

    static boolean isUsableWebhookUrl(String webhookUrl) {
        if (!isConfiguredValue(webhookUrl)) {
            return false;
        }

        String lower = webhookUrl.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isConfiguredValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();
        return !trimmed.startsWith("YOUR_") && !trimmed.endsWith("_HERE");
    }
}
