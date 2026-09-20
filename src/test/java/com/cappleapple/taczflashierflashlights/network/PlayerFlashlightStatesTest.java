package com.cappleapple.taczflashierflashlights.network;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerFlashlightStatesTest {
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final PlayerFlashlightStates states = new PlayerFlashlightStates();

    @Test
    void playersStartEnabledAndTogglingOneDoesNotChangeAnother() {
        assertTrue(states.isEnabled(first));
        assertFalse(states.setEnabled(first, true));
        assertTrue(states.setEnabled(first, false));
        assertFalse(states.isEnabled(first));
        assertTrue(states.isEnabled(second));
        assertFalse(states.setEnabled(first, false));
        assertTrue(states.setEnabled(first, true));
        assertTrue(states.isEnabled(first));
    }

    @Test
    void trackingHasNoDuplicatesAndStoppingOnlyRemovesTheRequestedRelationship() {
        states.startTracking(first, second);
        states.startTracking(first, second);
        states.startTracking(third, second);
        states.startTracking(first, third);
        states.startTracking(second, second);
        assertEquals(Set.of(first, third), states.trackingPlayers(second));
        states.stopTracking(first, second);
        assertEquals(Set.of(third), states.trackingPlayers(second));
        assertEquals(Set.of(first), states.trackingPlayers(third));
        states.stopTracking(third, second);
        assertTrue(states.trackingPlayers(second).isEmpty());
    }

    @Test
    void logoutDropsStateAndBothDirectionsOfTrackingWithoutAffectingOtherPlayers() {
        states.setEnabled(first, false);
        states.setEnabled(second, false);
        states.startTracking(first, second);
        states.startTracking(second, first);
        states.startTracking(third, second);

        states.removePlayer(first);

        assertTrue(states.isEnabled(first));
        assertFalse(states.isEnabled(second));
        assertTrue(states.trackingPlayers(first).isEmpty());
        assertEquals(Set.of(third), states.trackingPlayers(second));
    }

    @Test
    void stoppingServerClearsStateAndTrackingForTheNextWorld() {
        states.setEnabled(first, false);
        states.startTracking(second, first);
        states.clear();
        assertTrue(states.isEnabled(first));
        assertTrue(states.trackingPlayers(first).isEmpty());
    }

    @Test
    void observerSnapshotsCannotChangeTheStoreAndRemainStableDuringCleanup() {
        states.startTracking(first, second);
        Set<UUID> snapshot = states.trackingPlayers(second);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(third));
        states.removePlayer(first);
        assertEquals(Set.of(first), snapshot);
        assertTrue(states.trackingPlayers(second).isEmpty());
    }
}
