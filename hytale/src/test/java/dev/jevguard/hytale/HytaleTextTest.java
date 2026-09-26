package dev.jevguard.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jevguard.hytale.HytaleText.Segment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HytaleTextTest {
    @Test
    void colorsNestAndClose() {
        List<Segment> s = HytaleText.parse("<dark_red>[JevGuard]</dark_red> <red>Blocked</red>", Map.of());
        assertEquals(List.of(
                new Segment("[JevGuard]", "#AA0000", false, false),
                new Segment(" ", null, false, false),
                new Segment("Blocked", "#FF5555", false, false)), s);
    }

    @Test
    void placeholdersAreInsertedAsPlainTextInCurrentStyle() {
        List<Segment> s = HytaleText.parse("<gray><bold><player></bold>: <message></gray>",
                Map.of("player", "Steve", "message", "<red>not a tag</red>"));
        assertEquals(List.of(
                new Segment("Steve", "#AAAAAA", true, false),
                new Segment(": ", "#AAAAAA", false, false),
                new Segment("<red>not a tag</red>", "#AAAAAA", false, false)), s);
    }

    @Test
    void unknownTagsStayLiteral() {
        List<Segment> s = HytaleText.parse("<gray><usage></gray>", Map.of("usage", "/jevguard <reload|status>"));
        assertEquals("/jevguard <reload|status>", s.getFirst().text());
        assertEquals("a <weird> tag", HytaleText.parse("a <weird> tag", Map.of()).getFirst().text());
    }

    @Test
    void hexColors() {
        assertEquals("#12abef", HytaleText.parse("<#12ABEF>x</#12abef>", Map.of()).getFirst().color());
    }
}
