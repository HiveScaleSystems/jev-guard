package dev.jevguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GuardConfigTest {
    private static Map<String, Object> bundled() {
        try (InputStream in = GuardConfigTest.class.getResourceAsStream("/config.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void bundledConfigLoadsWithEnvKey() {
        GuardConfig c = GuardConfig.load(bundled(), Map.of("TYPESAFE_API_KEY", "ts_x")::get, "Minecraft");
        assertEquals(Mode.SHADOW, c.mode());
        assertEquals("typesafe", c.target().name());
        assertEquals(7, c.moderation().categories().size());
        assertTrue(c.failOpen());
        assertTrue(c.messages().staffBlocked().contains("<probability>"));
    }

    @Test
    void cloudflareProviderReadsEnvSecrets() {
        Map<String, Object> yaml = bundled();
        @SuppressWarnings("unchecked")
        Map<String, Object> api = (Map<String, Object>) yaml.get("api");
        api.put("provider", "cloudflare");
        GuardConfig c = GuardConfig.load(yaml, Map.of("CLOUDFLARE_ACCOUNT_ID", "acc", "CLOUDFLARE_API_TOKEN", "tok")::get, "Minecraft");
        assertEquals("cloudflare", c.target().name());
        assertEquals("https://api.cloudflare.com/client/v4/accounts/acc/ai/run", c.target().uri().toString());
    }

    @Test
    void missingKeyIsAClearError() {
        var e = assertThrows(IllegalArgumentException.class, () -> GuardConfig.load(bundled(), k -> null, "Minecraft"));
        assertTrue(e.getMessage().contains("TYPESAFE_API_KEY"));
    }
}
