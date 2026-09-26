package dev.jevguard.hytale;

import com.hypixel.hytale.server.core.Message;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders jev-guard templates to Hytale {@link Message}s. Hytale has no markup parser, so this
 * supports the subset jev-guard's config uses: MiniMessage color names, {@code <#rrggbb>},
 * {@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>}, closing tags, and {@code <name>}
 * placeholders. Placeholder values are inserted as plain text; unknown tags are kept literally.
 */
final class HytaleText {
    static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000AA"), Map.entry("dark_green", "#00AA00"),
            Map.entry("dark_aqua", "#00AAAA"), Map.entry("dark_red", "#AA0000"), Map.entry("dark_purple", "#AA00AA"),
            Map.entry("gold", "#FFAA00"), Map.entry("gray", "#AAAAAA"), Map.entry("grey", "#AAAAAA"),
            Map.entry("dark_gray", "#555555"), Map.entry("dark_grey", "#555555"), Map.entry("blue", "#5555FF"),
            Map.entry("green", "#55FF55"), Map.entry("aqua", "#55FFFF"), Map.entry("red", "#FF5555"),
            Map.entry("light_purple", "#FF55FF"), Map.entry("yellow", "#FFFF55"), Map.entry("white", "#FFFFFF"));

    /** One run of text with the style active at that point. Package-private for tests. */
    record Segment(String text, String color, boolean bold, boolean italic) {
    }

    private record Style(String tag, String color, boolean bold, boolean italic) {
    }

    private HytaleText() {
    }

    static Message render(String template, Map<String, String> placeholders) {
        List<Message> parts = new ArrayList<>();
        for (Segment s : parse(template, placeholders)) {
            Message m = Message.raw(s.text());
            if (s.color() != null) {
                m = m.color(s.color());
            }
            if (s.bold()) {
                m = m.bold(true);
            }
            if (s.italic()) {
                m = m.italic(true);
            }
            parts.add(m);
        }
        return parts.isEmpty() ? Message.empty() : Message.join(parts.toArray(Message[]::new));
    }

    static List<Segment> parse(String template, Map<String, String> placeholders) {
        List<Segment> out = new ArrayList<>();
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(new Style("", null, false, false));
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            int close = c == '<' ? template.indexOf('>', i) : -1;
            if (close < 0) {
                text.append(c);
                i++;
                continue;
            }
            String tag = template.substring(i + 1, close);
            String name = tag.toLowerCase(Locale.ROOT);
            Style top = stack.peek();
            if (name.startsWith("/")) {
                String closing = name.substring(1);
                if (stack.size() > 1 && (closing.isEmpty() || top.tag().equals(closing))) {
                    flush(out, text, top);
                    stack.pop();
                } else {
                    text.append('<').append(tag).append('>');
                }
            } else if (placeholders.containsKey(tag)) {
                flush(out, text, top);
                out.add(new Segment(placeholders.get(tag), top.color(), top.bold(), top.italic()));
            } else if (COLORS.containsKey(name) || name.matches("#[0-9a-f]{6}")) {
                flush(out, text, top);
                stack.push(new Style(name, COLORS.getOrDefault(name, name), top.bold(), top.italic()));
            } else if (name.equals("bold") || name.equals("b")) {
                flush(out, text, top);
                stack.push(new Style(name, top.color(), true, top.italic()));
            } else if (name.equals("italic") || name.equals("i")) {
                flush(out, text, top);
                stack.push(new Style(name, top.color(), top.bold(), true));
            } else {
                text.append('<').append(tag).append('>');
            }
            i = close + 1;
        }
        flush(out, text, stack.peek());
        return out;
    }

    private static void flush(List<Segment> out, StringBuilder text, Style style) {
        if (!text.isEmpty()) {
            out.add(new Segment(text.toString(), style.color(), style.bold(), style.italic()));
            text.setLength(0);
        }
    }
}
