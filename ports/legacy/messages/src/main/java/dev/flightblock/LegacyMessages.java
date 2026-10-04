package dev.flightblock;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The old chat API needs a sender UUID; action-bar messages use displayClientMessage. */
public final class LegacyMessages {
    private LegacyMessages() { }
    public static void send(ServerPlayer player, Component message) { player.sendMessage(message, Util.NIL_UUID); }
    public static void send(ServerPlayer player, Component message, boolean overlay) { player.displayClientMessage(message, overlay); }
}
