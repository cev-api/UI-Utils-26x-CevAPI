package com.ui_utils.uiutils.macro;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.Packet;

public final class UiUtilsMacroRuntimeState {
    public record Sound(long sequence, String id, double x, double y, double z) {}
    public record Chat(long sequence, String text, boolean server) {}
    private static final AtomicLong incomingCount = new AtomicLong(), outgoingCount = new AtomicLong(), chatCount = new AtomicLong(), soundCount = new AtomicLong();
    private static final Map<String, Long> incomingEvents = new ConcurrentHashMap<>(), outgoingEvents = new ConcurrentHashMap<>();
    private static final Map<String, Packet<?>> outgoing = new ConcurrentHashMap<>();
    private static final Map<String, Integer> peerSteps = new ConcurrentHashMap<>();
    private static final Deque<Sound> sounds = new ArrayDeque<>();
    private static final Deque<Chat> chats = new ArrayDeque<>();
    private static volatile String lastIncomingPacket = "", lastOutgoingPacket = "", lastChatMessage = "";
    private static volatile long serverTick = -1;
    private static final java.util.regex.Pattern STEP = java.util.regex.Pattern.compile("\\[UIUtilsStep:([A-Za-z0-9_-]+):([A-Za-z0-9_-]+):(\\d+)\\]");
    private UiUtilsMacroRuntimeState() {}
    public static void onIncomingPacket(String name) { lastIncomingPacket = normalize(name); incomingEvents.put(lastIncomingPacket, incomingCount.incrementAndGet()); }
    public static void onOutgoingPacket(String name) { lastOutgoingPacket = normalize(name); outgoingEvents.put(lastOutgoingPacket, outgoingCount.incrementAndGet()); }
    public static void onIncomingPacket(Packet<?> packet) {
        String name = normalize(packet.getClass().getSimpleName());
        long sequence = incomingCount.incrementAndGet(); lastIncomingPacket = name;
        incomingEvents.put(name, sequence); incomingEvents.put(normalize(packet.type().id().toString()), sequence);
    }
    public static void onOutgoingPacket(Packet<?> packet) {
        String name = normalize(packet.getClass().getSimpleName());
        long sequence = outgoingCount.incrementAndGet(); lastOutgoingPacket = name;
        outgoingEvents.put(name, sequence); outgoingEvents.put(normalize(packet.type().id().toString()), sequence);
    }
    public static void observeOutgoing(Packet<?> packet) { outgoing.put(normalize(packet.getClass().getSimpleName()), packet); outgoing.put(normalize(packet.type().id().toString()), packet); }
    public static Packet<?> outgoingPacket(String name) { return outgoing.get(normalize(name)); }
    public static synchronized void onSound(String id, double x, double y, double z) {
        sounds.addLast(new Sound(soundCount.incrementAndGet(), id, x, y, z));
        while (sounds.size() > 256) sounds.removeFirst();
    }
    public static void onChatMessage(String text) { onChatMessage(text, false); }
    public static synchronized void onChatMessage(String text, boolean server) {
        lastChatMessage = text == null ? "" : text;
        chats.addLast(new Chat(chatCount.incrementAndGet(), lastChatMessage, server));
        while (chats.size() > 256) chats.removeFirst();
        var match = STEP.matcher(lastChatMessage);
        if (match.find()) try {
            peerSteps.put(decode(match.group(1)) + "\n" + decode(match.group(2)), Integer.parseInt(match.group(3)));
        } catch (IllegalArgumentException ignored) {}
    }
    public static String stepMessage(String peer, String macro, int step) { return "[UIUtilsStep:" + encode(peer) + ":" + encode(macro) + ":" + step + "]"; }
    private static String encode(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String decode(String s) { return new String(Base64.getUrlDecoder().decode(s), java.nio.charset.StandardCharsets.UTF_8); }
    public static int peerStep(String peer, String macro) { return peerSteps.getOrDefault(peer + "\n" + macro, -1); }
    public static synchronized List<Sound> sounds() { return List.copyOf(sounds); }
    public static synchronized List<Chat> chats() { return List.copyOf(chats); }
    public static boolean packetSince(boolean incoming, String name, long start) {
        return (incoming ? incomingEvents : outgoingEvents).entrySet().stream().anyMatch(entry -> entry.getValue() > start && entry.getKey().contains(normalize(name)));
    }
    public static long chatCount() { return chatCount.get(); }
    public static long soundCount() { return soundCount.get(); }
    public static long serverTick() { return serverTick; }
    public static void serverTime(long tick) { serverTick = tick; }
    public static long incomingCount() { return incomingCount.get(); }
    public static long outgoingCount() { return outgoingCount.get(); }
    public static String lastIncomingPacket() { return lastIncomingPacket; }
    public static String lastOutgoingPacket() { return lastOutgoingPacket; }
    public static String lastChatMessage() { return lastChatMessage; }
    public static synchronized void reset() { incomingEvents.clear(); outgoingEvents.clear(); outgoing.clear(); peerSteps.clear(); chats.clear(); sounds.clear(); serverTick = -1; lastChatMessage = ""; }
    public static String normalize(String name) {
        if (name == null) return "";
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (s.contains(":")) s = s.substring(s.lastIndexOf(':') + 1);
        if (s.contains(".")) s = s.substring(s.lastIndexOf('.') + 1);
        return s.endsWith("packet") ? s.substring(0, s.length() - 6) : s;
    }
}
