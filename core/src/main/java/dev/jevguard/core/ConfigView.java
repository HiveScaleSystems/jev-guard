package dev.jevguard.core;

import java.util.List;
import java.util.Map;

/** Dotted-path reads over a parsed YAML map, with defaults. */
final class ConfigView {
    private final Map<?, ?> root;

    ConfigView(Map<?, ?> root) {
        this.root = root == null ? Map.of() : root;
    }

    Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    String string(String path, String def) {
        Object v = get(path);
        return v == null ? def : String.valueOf(v);
    }

    double number(String path, double def) {
        return get(path) instanceof Number n ? n.doubleValue() : def;
    }

    int integer(String path, int def) {
        return get(path) instanceof Number n ? n.intValue() : def;
    }

    long longValue(String path, long def) {
        return get(path) instanceof Number n ? n.longValue() : def;
    }

    boolean bool(String path, boolean def) {
        return get(path) instanceof Boolean b ? b : def;
    }

    List<String> strings(String path) {
        return get(path) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    Map<?, ?> section(String path) {
        return get(path) instanceof Map<?, ?> map ? map : Map.of();
    }
}
