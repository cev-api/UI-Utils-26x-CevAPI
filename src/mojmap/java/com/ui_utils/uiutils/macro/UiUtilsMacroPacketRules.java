package com.ui_utils.uiutils.macro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Temporary macro rules; never persisted into the user's Packet Tools settings. */
public final class UiUtilsMacroPacketRules {
    public enum Mode { ALLOW, DENY, DELAY }
    public record Rule(Mode mode, int delayTicks, Object connection, long expiresAt) {}
    private static final Map<String, Rule> RULES = new HashMap<>();
    private UiUtilsMacroPacketRules() {}

    public static synchronized void configure(Object connection, String direction, List<String> names,
        Mode mode, int delayTicks, int durationMs) {
        if (connection == null) throw new IllegalStateException("Not connected");
        if (!direction.equals("C2S") && !direction.equals("S2C")) throw new IllegalArgumentException("Invalid packet direction");
        if (names.isEmpty()) throw new IllegalArgumentException("Choose at least one packet");
        if (delayTicks < 1 || delayTicks > 9999 || durationMs < 0 || durationMs > 300000)
            throw new IllegalArgumentException("Invalid packet delay or rule duration");
        long expiry = durationMs == 0 ? Long.MAX_VALUE : System.nanoTime() + durationMs * 1_000_000L;
        for (String name : names) {
            String key = direction + ":" + name.trim().toLowerCase(java.util.Locale.ROOT);
            if (mode == Mode.ALLOW) RULES.remove(key);
            else RULES.put(key, new Rule(mode, delayTicks, connection, expiry));
        }
    }

    public static synchronized Rule get(Object connection, String direction, String name) {
        String key = direction + ":" + name.trim().toLowerCase(java.util.Locale.ROOT);
        Rule rule = RULES.get(key);
        if (rule != null && (rule.connection() != connection || System.nanoTime() >= rule.expiresAt())) {
            RULES.remove(key);
            return null;
        }
        return rule;
    }
    public static synchronized void clear() { RULES.clear(); }
    public static synchronized void disconnect(Object connection) {
        RULES.values().removeIf(rule -> rule.connection() == connection);
    }
}
