package com.cappleapple.taczflashierflashlights.network;

import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Optional server relay for the attachment flashlight toggle. */
public final class FlashlightNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("taczopenlights", "flashlight"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));
    private static final PlayerFlashlightStates SERVER_STATES = new PlayerFlashlightStates();
    private static BiConsumer<UUID, Boolean> clientReceiver = (player, enabled) -> {};
    private static Supplier<Connection> clientConnection = () -> null;
    private static boolean registered;

    private FlashlightNetwork() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(SetEnabled.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeBoolean(message.enabled()))
                .decoder(buffer -> new SetEnabled(buffer.readBoolean()))
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null && SERVER_STATES.setEnabled(sender.getUUID(), message.enabled())) {
                        broadcastState(sender);
                    }
                }).add();
        CHANNEL.messageBuilder(PlayerState.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerState::encode)
                .decoder(PlayerState::decode)
                .consumerMainThread((message, context) ->
                        clientReceiver.accept(message.player(), message.enabled()))
                .add();

        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onLogin);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onLogout);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onStopTracking);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(FlashlightNetwork::onServerStopped);
    }

    /** Bind from client initialization; callbacks run on the client main thread. */
    public static void setClientReceiver(BiConsumer<UUID, Boolean> receiver) {
        clientReceiver = Objects.requireNonNull(receiver);
    }

    /** The supplier must return null while disconnected. Bind only on the client. */
    public static void setClientConnectionSupplier(Supplier<Connection> connection) {
        clientConnection = Objects.requireNonNull(connection);
    }

    public static boolean serverPresent() {
        Connection connection = clientConnection.get();
        return connection != null && connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    /** Returns false when the server does not have this optional addon channel. */
    public static boolean sendLocalState(boolean enabled) {
        Connection connection = clientConnection.get();
        if (connection == null || !connection.isConnected() || !CHANNEL.isRemotePresent(connection)) {
            return false;
        }
        CHANNEL.sendTo(new SetEnabled(enabled), connection, NetworkDirection.PLAY_TO_SERVER);
        return true;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SERVER_STATES.setEnabled(player.getUUID(), true);
            broadcastState(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SERVER_STATES.removePlayer(player.getUUID());
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            SERVER_STATES.startTracking(observer.getUUID(), target.getUUID());
            sendState(observer, new PlayerState(target.getUUID(), SERVER_STATES.isEnabled(target.getUUID())));
        }
    }

    private static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            SERVER_STATES.stopTracking(observer.getUUID(), target.getUUID());
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcastState(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            broadcastState(player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SERVER_STATES.clear();
    }

    private static void broadcastState(ServerPlayer player) {
        PlayerState state = new PlayerState(player.getUUID(), SERVER_STATES.isEnabled(player.getUUID()));
        sendState(player, state);
        if (player.getServer() != null) {
            for (UUID observerId : SERVER_STATES.trackingPlayers(player.getUUID())) {
                ServerPlayer observer = player.getServer().getPlayerList().getPlayer(observerId);
                if (observer != null) {
                    sendState(observer, state);
                }
            }
        }
    }

    private static void sendState(ServerPlayer recipient, PlayerState state) {
        Connection connection = recipient.connection.connection;
        if (connection.isConnected() && CHANNEL.isRemotePresent(connection)) {
            CHANNEL.sendTo(state, connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    private record SetEnabled(boolean enabled) {}

    private record PlayerState(UUID player, boolean enabled) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(player);
            buffer.writeBoolean(enabled);
        }

        private static PlayerState decode(FriendlyByteBuf buffer) {
            return new PlayerState(buffer.readUUID(), buffer.readBoolean());
        }
    }
}
