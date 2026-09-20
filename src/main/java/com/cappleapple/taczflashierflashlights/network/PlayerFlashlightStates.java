package com.cappleapple.taczflashierflashlights.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session state, accessed only from the server thread. */
final class PlayerFlashlightStates {
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Map<UUID, Set<UUID>> trackingPlayers = new HashMap<>();

    boolean isEnabled(UUID player) {
        return !disabledPlayers.contains(player);
    }

    /** Returns whether the effective state changed. Enabled is the default. */
    boolean setEnabled(UUID player, boolean enabled) {
        return enabled ? disabledPlayers.remove(player) : disabledPlayers.add(player);
    }

    void startTracking(UUID observer, UUID target) {
        if (!observer.equals(target)) {
            trackingPlayers.computeIfAbsent(target, ignored -> new HashSet<>()).add(observer);
        }
    }

    void stopTracking(UUID observer, UUID target) {
        Set<UUID> observers = trackingPlayers.get(target);
        if (observers != null) {
            observers.remove(observer);
            if (observers.isEmpty()) {
                trackingPlayers.remove(target);
            }
        }
    }

    Set<UUID> trackingPlayers(UUID target) {
        Set<UUID> observers = trackingPlayers.get(target);
        return observers == null ? Set.of() : Set.copyOf(observers);
    }

    void removePlayer(UUID player) {
        disabledPlayers.remove(player);
        trackingPlayers.remove(player);
        trackingPlayers.values().forEach(observers -> observers.remove(player));
        trackingPlayers.values().removeIf(Set::isEmpty);
    }

    void clear() {
        disabledPlayers.clear();
        trackingPlayers.clear();
    }
}
