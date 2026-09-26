package dev.jevguard.paper;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Renders jev-guard templates with MiniMessage. Placeholders are unparsed, so player text can't inject tags. */
final class PaperText {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private PaperText() {
    }

    static Component render(String template, Map<String, String> placeholders) {
        TagResolver.Builder resolvers = TagResolver.builder();
        placeholders.forEach((k, v) -> resolvers.resolver(Placeholder.unparsed(k, v)));
        return MINI.deserialize(template, resolvers.build());
    }
}
